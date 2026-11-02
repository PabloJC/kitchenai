package com.kitchenai.ui.presentation.suggestions.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.HouseholdAvoids
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.MealType
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.householdAvoids
import com.kitchenai.shared.domain.usecase.recipe.GetStoredSuggestionsUseCase
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantryUseCase
import com.kitchenai.shared.domain.usecase.recipe.StoreSuggestionsUseCase
import com.kitchenai.shared.domain.usecase.recipe.SuggestRecipesUseCase
import com.kitchenai.ui.platform.NetworkMonitor
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.RecipePictures
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_action
import com.kitchenai.ui.resources.error_unauthorized_suggestions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Suggestions, ready on arrival.
 *
 * A launch shows whatever was stored from the last one immediately, then starts exactly one
 * generation — never a second, however many times the tab is re-entered within the same
 * session, which is what [started] guards. A model call costs money and takes the better part
 * of a minute; the one thing that must never happen again is one firing per tab tap (#52, #133).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsViewModel(
    private val suggestRecipes: SuggestRecipesUseCase,
    private val getStoredSuggestions: GetStoredSuggestionsUseCase,
    private val storeSuggestions: StoreSuggestionsUseCase,
    private val matchRecipe: MatchRecipeAgainstPantryUseCase,
    private val reads: SuggestionsReadsDelegate,
    private val writes: SuggestionsWritesDelegate,
    private val network: NetworkMonitor,
    private val pictures: RecipePictures,
) : ViewModel() {
    private val internalState = MutableStateFlow(SuggestionsUiState())
    val state: StateFlow<SuggestionsUiState> = internalState.asStateFlow()

    private val eventChannel = Channel<SuggestionsEvent>(Channel.BUFFERED)
    val events: Flow<SuggestionsEvent> = eventChannel.receiveAsFlow()

    // The catalogue names the lines a suggestion carries. It streams because the screen may be
    // open before the catalogue has arrived, and a suggestion generated later must still resolve.
    private val ingredients = MutableStateFlow<List<Ingredient>>(emptyList())

    // What is on screen in domain shape, so it can be re-resolved into words again once the
    // catalogue answers, rather than only at the moment it was first shown.
    private var current: List<RecipeSuggestion> = emptyList()
    private var currentSaved: List<RecipeSuggestion> = emptyList()

    // The generations before [current], newest first: New ideas must not cost the ones still wanted.
    private var earlier: List<List<RecipeSuggestion>> = emptyList()

    // Every member's Avoid set, off the kitchen already watched: it flags a kept dish that conflicts.
    private var avoids = HouseholdAvoids()
    private var user: UserId? = null
    private var languageTags: List<String> = emptyList()
    private var listLabels: Map<String, String> = emptyMap()
    private var started = false

    // The kitchen a user belongs to can change while this screen is open (joining another one),
    // so it is a listener like the pantry itself, not a value captured once at start().
    private val kitchenId = MutableStateFlow<KitchenId?>(null)

    // generate()'s own launch runs on viewModelScope, not as a child of the collectLatest below,
    // so a kitchen change does not cancel it for free the way collectLatest cancels its own
    // block. Tracked here so the kitchen-change path can cancel it explicitly.
    private val generation = MutableStateFlow<Job?>(null)

    // Tracked so a retry can replace a kitchen listener that failed before it ever answered.
    private var kitchenListener: Job? = null

    /** Idempotent: a configuration change composes the screen again and must not double the listener or the launch. */
    fun start(
        userId: UserId,
        languageTags: List<String>,
        defaultListName: String,
    ) {
        user = userId
        this.languageTags = languageTags
        // Only used if this screen is the one that creates the list, which the session gate
        // normally does first.
        listLabels = languageTags.take(1).associateWith { defaultListName }
        if (started) return
        started = true
        viewModelScope.launch {
            reads.ingredients().collect { loaded ->
                ingredients.value = loaded
                // The stored set can be on screen already, showing catalogue ids where the
                // resolver had nothing yet: a local read is near-instant and does not wait for
                // this listener the way the 20-60s model call used to.
                reresolve()
            }
        }
        viewModelScope.launch {
            network.isOnline.collect { online -> internalState.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch { pictures.all.collect { reresolve() } }
        watchKitchen(userId)
        viewModelScope.launch {
            // matchRecipe reads the pantry itself, but only once per call — combine so a saved
            // recipe's coverage is recomputed on a pantry change too, not only when the saved
            // list itself does.
            kitchenId.filterNotNull().flatMapLatest { id ->
                combine(reads.savedRecipes(id), reads.pantry(id)) { recipes, _ -> id to recipes }
            }.collect { (id, recipes) -> matchSaved(id, recipes) }
        }
        viewModelScope.launch {
            // The stamped match is a snapshot of the pantry at generation time. distinctUntilChanged
            // keeps a snapshot that re-emits unchanged holdings from costing a pass of reads.
            kitchenId.filterNotNull().flatMapLatest { id ->
                reads.pantry(id)
                    .onEach { held -> internalState.update { it.copy(isPantryEmpty = held.isEmpty()) } }
                    .distinctUntilChanged()
                    .map { id }
            }.collectLatest { id -> rematchGenerated(id) }
        }
        viewModelScope.launch {
            // A kitchen change re-runs both: a different kitchen's pantry needs a fresh
            // generation. The previous kitchen's generation is cancelled explicitly first:
            // collectLatest only cancels this block's own coroutine, not generate()'s independent
            // viewModelScope launch, so a stale answer would otherwise land as the new kitchen's.
            kitchenId.filterNotNull().collectLatest { id ->
                generation.value?.cancel()
                internalState.update { it.copy(isGenerating = false) }
                showStored(id)
                // An empty pantry would spend a model call on "nothing to suggest"; the screen
                // points at the pantry instead. A listener that fails without emitting still generates.
                if (reads.pantry(id).firstOrNull()?.isEmpty() != true) generate()
            }
        }
    }

    /**
     * What Try again means depends on what failed: a kitchen that never answered is listened to
     * again; anything else was a run, and runs again.
     */
    fun retry() {
        val userId = user ?: return
        if (kitchenId.value != null) return generate()
        internalState.update { it.copy(error = null) }
        kitchenListener?.cancel()
        watchKitchen(userId)
    }

    private fun watchKitchen(userId: UserId) {
        kitchenListener =
            viewModelScope.launch {
                launch {
                    reads.kitchen(userId).collect { kitchen ->
                        kitchenId.value = kitchen.id
                        avoids = kitchen.householdAvoids()
                        reresolve()
                    }
                }
                launch {
                    reads.kitchen.errors(userId).collect { error ->
                        val message = error.describe(Res.string.error_unauthorized_suggestions)
                        internalState.update { it.copy(error = message) }
                    }
                }
            }
    }

    fun setMaxResults(value: Int) = editOptions { it.copy(maxResults = value) }

    fun setMaxMinutes(value: Int?) = editOptions { it.copy(maxMinutes = value) }

    fun setUseOnlyPantry(value: Boolean) = editOptions { it.copy(useOnlyPantry = value) }

    fun setUseExpiringSoon(value: Boolean) = editOptions { it.copy(useExpiringSoon = value) }

    fun setMealType(value: MealType?) = editOptions { it.copy(mealType = value) }

    /**
     * A second tap while one is in flight is ignored rather than queued: two runs would bill
     * twice for an answer only one of which could be shown.
     *
     * Safe against the launch auto-generate racing a tap on the button precisely because neither
     * this nor [start] ever names a dispatcher: both run confined to [viewModelScope]'s own Main,
     * so the guard below and the auto-generate's call to this are never truly concurrent.
     */
    fun generate() {
        val userId = user ?: return
        val kitchen = kitchenId.value ?: return
        // The button is disabled offline; this also stops the arrival run from failing on its own.
        if (internalState.value.isGenerating || !internalState.value.isOnline) return
        // The options this run is sent with, so an edit made while it is in flight still reads
        // as a change once it lands.
        val sent = internalState.value.options
        internalState.update { it.copy(isGenerating = true, error = null) }
        generation.value =
            viewModelScope.launch {
                val answered = suggestRecipes(userId, kitchen, languageTags, sent.toDomain())
                // A data source that maps a cancellation to an AppError must not turn it into a failure.
                ensureActive()
                when (answered) {
                    is AppResult.Failure -> fail(answered.error)
                    is AppResult.Success -> generated(kitchen, answered.data, sent)
                }
            }
    }

    /** Stops the in-flight run; what was on screen before it is still there, since only a landing replaces it. */
    fun cancelGeneration() {
        if (!internalState.value.isGenerating) return
        generation.value?.cancel()
        internalState.update { it.copy(isGenerating = false) }
    }

    /**
     * Hands over the recipe in hand rather than its id: a generated dish is in no repository, so
     * re-reading it would fail. A second tap while one is writing is ignored.
     */
    fun addMissingToList(id: RecipeId) {
        val kitchen = kitchenId.value ?: return
        if (internalState.value.addingMissingFor != null) return
        val recipe = (current + earlier.flatten() + currentSaved).firstOrNull { it.recipe.id == id }?.recipe ?: return
        internalState.update { it.copy(addingMissingFor = id) }
        viewModelScope.launch {
            val outcome =
                when (val list = writes.defaultList(kitchen, listLabels)) {
                    is AppResult.Failure -> list
                    is AppResult.Success -> writes.addMissing(kitchen, list.data, recipe, recipe.servings)
                }
            internalState.update { it.copy(addingMissingFor = null) }
            when (outcome) {
                is AppResult.Failure ->
                    eventChannel.send(
                        SuggestionsEvent.Failed(outcome.error.describe(Res.string.error_unauthorized_action)),
                    )
                is AppResult.Success ->
                    eventChannel.send(SuggestionsEvent.AddedToList(outcome.data.added, outcome.data.skipped))
            }
        }
    }

    fun showSaved(showing: Boolean) = internalState.update { it.copy(showingSaved = showing) }

    /** Which earlier generation is on screen, 0 the newest of them; null is back to the latest. */
    fun browseEarlier(index: Int?) =
        internalState.update { it.copy(browsingEarlier = index?.takeIf { shown -> shown in earlier.indices }) }

    /** The card's heart: the same save as the detail screen's, and an unsave with the same undo. */
    fun toggleSaved(id: RecipeId) {
        if (currentSaved.any { it.recipe.id == id }) return unsave(id)
        val kitchen = kitchenId.value ?: return
        val recipe = (current + earlier.flatten()).firstOrNull { it.recipe.id == id }?.recipe ?: return
        viewModelScope.launch {
            when (val outcome = writes.save(kitchen, recipe)) {
                is AppResult.Failure ->
                    eventChannel.send(
                        SuggestionsEvent.Failed(outcome.error.describe(Res.string.error_unauthorized_action)),
                    )
                is AppResult.Success -> eventChannel.send(SuggestionsEvent.Saved)
            }
        }
    }

    fun searchSaved(query: String) = internalState.update { it.copy(savedQuery = query).withVisibleSaved() }

    /** The same removal as the detail screen's heart, from the card, with an undo. */
    fun unsave(id: RecipeId) {
        val kitchen = kitchenId.value ?: return
        val recipe = currentSaved.firstOrNull { it.recipe.id == id }?.recipe ?: return
        viewModelScope.launch {
            when (val outcome = writes.unsave(kitchen, id)) {
                is AppResult.Failure ->
                    eventChannel.send(
                        SuggestionsEvent.Failed(outcome.error.describe(Res.string.error_unauthorized_action)),
                    )
                is AppResult.Success -> eventChannel.send(SuggestionsEvent.Unsaved(recipe))
            }
        }
    }

    fun undoUnsave(recipe: Recipe) {
        val kitchen = kitchenId.value ?: return
        viewModelScope.launch {
            val outcome = writes.save(kitchen, recipe)
            if (outcome is AppResult.Failure) {
                eventChannel.send(SuggestionsEvent.Failed(outcome.error.describe(Res.string.error_unauthorized_action)))
            }
        }
    }

    private fun editOptions(block: (SuggestionOptionsUi) -> SuggestionOptionsUi) =
        internalState.update { current -> current.copy(options = block(current.options)) }

    /** What survived the last launch, shown while this one has nothing of its own yet. */
    private suspend fun showStored(kitchenId: KitchenId) {
        val stored = (getStoredSuggestions(kitchenId) as? AppResult.Success)?.data
        if (stored.isNullOrEmpty()) return
        earlier = stored.drop(1)
        render(stored.first())
    }

    /** A new generation takes the screen; the one it replaces becomes the newest earlier one, on screen and on disk. */
    private suspend fun generated(
        kitchenId: KitchenId,
        suggestions: List<RecipeSuggestion>,
        sent: SuggestionOptionsUi,
    ) {
        // The write's own failure does not undo a run the user already sees the result of: it
        // only costs the next launch its head start.
        storeSuggestions(suggestions.map { it.recipe })
        // What was on screen becomes the newest earlier set, as many as the store keeps. A run that
        // found nothing still moves it back, so "nothing to suggest" never hides what came before.
        earlier = (listOf(current) + earlier).filter { it.isNotEmpty() }.take(EARLIER_KEPT)
        // The pantry may have changed during the minute-long call, and its emission found the
        // previous list on screen. A stamped match is only as fresh as the model call's start.
        render(withFreshMatch(kitchenId, suggestions))
        internalState.update {
            it.copy(
                isGenerating = false,
                hasGenerated = true,
                error = null,
                appliedOptions = sent,
                browsingEarlier = null,
            )
        }
    }

    private fun render(suggestions: List<RecipeSuggestion>) {
        current = suggestions
        reresolve()
    }

    /**
     * Generated suggestions re-matched on a pantry change, so shopping moves a card's coverage
     * without another model call. A result is applied only if the list it was computed for is
     * still the one on screen, in the same kitchen: a newer generation or a kitchen change that
     * landed while the reads were suspended must never receive a match computed for the old list.
     */
    private suspend fun rematchGenerated(kitchenId: KitchenId) {
        val snapshot = current
        val earlierSnapshot = earlier
        if (snapshot.isEmpty() && earlierSnapshot.isEmpty()) return
        val fresh = withFreshMatch(kitchenId, snapshot)
        val freshEarlier = earlierSnapshot.map { generation -> withFreshMatch(kitchenId, generation) }
        if (current !== snapshot || earlier !== earlierSnapshot || this.kitchenId.value != kitchenId) return
        current = fresh
        earlier = freshEarlier
        reresolve()
    }

    /** A line whose own re-match fails keeps the match it had: a read error is not the user's to see. */
    private suspend fun withFreshMatch(
        kitchenId: KitchenId,
        suggestions: List<RecipeSuggestion>,
    ): List<RecipeSuggestion> =
        suggestions.map { suggestion ->
            val match = matchRecipe(kitchenId, suggestion.recipe) as? AppResult.Success
            if (match == null) suggestion else suggestion.copy(match = match.data)
        }

    /**
     * A saved recipe carries no match of its own — matching is what a suggestion's orchestrator
     * stamps on the way out, and a save just keeps the [Recipe]. Computed here the same way
     * [RecipeDetailViewModel] does it, so the coverage bar means the same thing on every card.
     * [matchRecipe] itself reads the pantry once per call, not as a listener — [start] is what
     * re-invokes this whenever the pantry changes, so the result here does not go stale on its own.
     *
     * A recipe whose own match fails is dropped from the section rather than shown broken or
     * blamed with a banner: one card silently missing costs less than a section-wide error over
     * what is otherwise a working list.
     */
    private suspend fun matchSaved(
        kitchenId: KitchenId,
        recipes: List<Recipe>,
    ) {
        currentSaved =
            recipes.mapNotNull { recipe ->
                val match = matchRecipe(kitchenId, recipe) as? AppResult.Success ?: return@mapNotNull null
                RecipeSuggestion(recipe, match.data, recipe.source)
            }
        reresolve()
    }

    /** Re-applies the resolver to whatever is on screen, for when the catalogue answers after the fact. */
    private fun reresolve() {
        // With no tags the resolve chain is empty, every lookup misses, and a card names a
        // catalogue ingredient by its identifier. The card carries no quantities, so terms and
        // taxonomies are not needed here — only a language to answer in.
        val resolver = LabelResolver(ingredients = ingredients.value, languageTags = languageTags)
        val fetched = pictures.all.value

        // A card shows a photograph its detail fetched since this list was built.
        fun RecipeSuggestion.card() =
            toUi(resolver, avoids).let { ui -> fetched[ui.id]?.let { url -> ui.copy(imageUrl = url) } ?: ui }
        internalState.update { state ->
            state.copy(
                suggestions = current.map { suggestion -> suggestion.card() },
                earlier = earlier.map { generation -> generation.map { suggestion -> suggestion.card() } },
                // A kitchen change can leave fewer earlier generations than the one being browsed.
                browsingEarlier = state.browsingEarlier?.takeIf { it in earlier.indices },
                savedRecipes = currentSaved.map { suggestion -> suggestion.card() },
                savedIds = currentSaved.map { it.recipe.id }.toSet(),
            ).withVisibleSaved()
        }
    }

    /**
     * Recomputed whenever the saved list or the search changes, so the two never disagree. A
     * query only applies while its field is on screen: an unsave that takes the list back under
     * the threshold hides the field, and a filter nobody can see or clear would hide recipes.
     */
    private fun SuggestionsUiState.withVisibleSaved(): SuggestionsUiState {
        val query = if (offersSavedSearch) savedQuery else ""
        return copy(savedQuery = query, visibleSaved = savedRecipes.searchedFor(query).cookableFirst())
    }

    private fun fail(error: AppError) {
        val message = error.describe(Res.string.error_unauthorized_suggestions)
        // State only: the retry card stays on screen until the next run, so an event would repeat it.
        internalState.update { it.copy(isGenerating = false, hasGenerated = true, error = message) }
    }
}

/** Every kept generation but the one on screen. */
private const val EARLIER_KEPT = StoreSuggestionsUseCase.KEPT_GENERATIONS - 1
