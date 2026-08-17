package com.kitchenai.ui.presentation.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_missing_ingredients
import com.kitchenai.ui.resources.error_unauthorized_action
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One recipe, and what the pantry says about it right now.
 *
 * The servings stepper re-matches rather than only re-scaling the numbers on screen. Scaling
 * without re-matching would show amounts for four beside buckets still answering for two,
 * which is worse than having no stepper at all.
 */
class RecipeDetailViewModel(
    private val reads: RecipeDetailReadsDelegate,
    private val writes: RecipeDetailWritesDelegate,
) : ViewModel() {
    private val internalState = MutableStateFlow(RecipeDetailUiState())
    val state: StateFlow<RecipeDetailUiState> = internalState.asStateFlow()

    private val eventChannel = Channel<RecipeDetailEvent>(Channel.BUFFERED)
    val events: Flow<RecipeDetailEvent> = eventChannel.receiveAsFlow()

    // Not exposed and never collected on their own — everything here runs confined to Main, so
    // a plain `var` would be just as safe. Kept as flows only so `.value` reads stay uniform with
    // `internalState`'s; see #147 for whether that uniformity is worth keeping.
    private val names = MutableStateFlow<List<Ingredient>>(emptyList())
    private val units = MutableStateFlow<List<Term>>(emptyList())

    // The taxonomies themselves, not only their terms: a term whose language the user does not
    // read falls back to the tag its taxonomy declares, and that tag lives here.
    private val vocabularies = MutableStateFlow<List<Taxonomy>>(emptyList())
    private val recipe = MutableStateFlow<Recipe?>(null)
    private val currentMatch = MutableStateFlow<PantryMatch?>(null)
    private val loading = MutableStateFlow<Job?>(null)
    private var user: UserId? = null
    private var id: RecipeId? = null
    private var listLabels: Map<String, String> = emptyMap()
    private var languageTags: List<String> = emptyList()
    private var started = false

    fun start(
        userId: UserId,
        recipeId: RecipeId,
        languageTags: List<String>,
        defaultListName: String,
    ) {
        user = userId
        id = recipeId
        this.languageTags = languageTags
        // Only used if this screen is the one that creates the list. It should not be — the
        // session gate creates it first — but a list named by whoever got there first is worse
        // than one named twice.
        listLabels = languageTags.take(1).associateWith { defaultListName }
        if (started) return
        started = true
        viewModelScope.launch {
            reads.ingredients().collect { loaded ->
                names.value = loaded
                recipe.value?.let { held -> render(held, currentMatch.value) }
            }
        }
        watchUnits()
        load(recipeId, servings = null)
    }

    /**
     * The unit vocabulary, which the catalogue names rather than this app: "200" is not a
     * quantity, and the taxonomy that holds units is the one that declares that purpose.
     */
    private fun watchUnits() {
        viewModelScope.launch {
            // collectLatest and coroutineScope together: a second emission of the taxonomy list
            // must replace the per-taxonomy collectors, not add to them. Plain collect leaves
            // the previous ones running, so one unit ends up with a listener per emission.
            reads.taxonomies().collectLatest { published ->
                vocabularies.value = published
                coroutineScope {
                    published.filter { it.purpose == TaxonomyPurpose.UNITS }.forEach { unit ->
                        launch {
                            reads.taxonomy(unit.id).collect { terms ->
                                units.value = terms
                                recipe.value?.let { held -> render(held, currentMatch.value) }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Re-scales and re-matches together; neither is useful without the other. */
    fun setServings(servings: Int) {
        if (servings < 1) return
        internalState.update { it.copy(servings = servings) }
        id?.let { recipeId -> load(recipeId, servings) }
    }

    fun save() =
        act { userId ->
            val held = recipe.value ?: return@act null
            writes.save(userId, held).map { RecipeDetailEvent.Saved }
        }

    // Both writes hand over the recipe in hand rather than its id, for the same reason the read
    // and the match do: a generated dish is in no repository, so re-reading it would fail.
    fun addMissingToList() =
        act { userId ->
            val held = recipe.value ?: return@act null
            when (val list = writes.defaultList(userId, listLabels)) {
                is AppResult.Failure -> AppResult.Failure(list.error)
                is AppResult.Success ->
                    writes
                        .addMissing(userId, list.data, held, internalState.value.servings)
                        .map { summary -> RecipeDetailEvent.AddedToList(summary.added, summary.skipped) }
            }
        }

    fun cook() =
        // A cook refused for missing ingredients is not a failure to apologise for: it is the
        // answer, and the screen already lists which ones.
        act(validation = { UiText.of(Res.string.error_missing_ingredients) }) { userId ->
            val held = recipe.value ?: return@act null
            writes.cook(userId, held, internalState.value.servings).map { RecipeDetailEvent.Cooked }
        }

    /**
     * One match at a time, whoever asked for it. A slower earlier read landing last would put
     * the buckets back to a count the stepper has already left — the same disagreement the
     * servings override exists to prevent — and the stepper is not gated while a write runs, so
     * a tap really can overlap the refresh after a cook.
     */
    private fun matching(block: suspend () -> Unit) {
        // Plain cancel-then-assign. Every caller reaches here on Main and nothing below suspends
        // before the assignment, so no two of them can interleave inside this function; the
        // atomic swap this used to do was guarding against a background dispatcher that is gone.
        loading.value?.cancel()
        loading.value = viewModelScope.launch { block() }
    }

    private fun load(
        recipeId: RecipeId,
        servings: Int?,
    ) {
        val userId = user ?: return
        matching {
            // The last generation first: a generated dish was never written anywhere else, so
            // the repository would answer NotFound for the only kind of recipe this screen is
            // reached with. A failed local read falls through to the repository, same as a miss.
            val stored = (reads.storedRecipe(recipeId) as? AppResult.Success)?.data
            if (stored != null) {
                matched(userId, stored, servings)
                return@matching
            }
            when (val found = reads.recipe(recipeId)) {
                is AppResult.Failure -> failed(found.error)
                is AppResult.Success -> matched(userId, found.data, servings)
            }
        }
    }

    /**
     * The buckets after a cook, from the recipe in hand. Never by id: a generated dish may have
     * left the cache by now, and the repository would answer NotFound for a cook that just
     * succeeded.
     *
     * Reads the servings when it runs rather than when it was queued, so a stepper tap that
     * overlapped the cook is answered for rather than overwritten.
     *
     * Silent when the re-match fails. The pantry has already changed and cannot be put back, so
     * a stale bucket is a smaller lie than telling somebody their cook did not happen.
     */
    private fun refreshMatch(userId: UserId) {
        val held = recipe.value ?: return
        matching {
            val servings = internalState.value.servings
            val match = reads.match(userId, held, servings)
            if (match is AppResult.Success) {
                currentMatch.value = match.data
                render(held, match.data, servings)
            }
        }
    }

    private suspend fun matched(
        userId: UserId,
        found: Recipe,
        servings: Int?,
    ) {
        recipe.value = found
        // Matched against the recipe in hand, never re-read by id: a generated dish is not in
        // any repository, and asking for it again is the failure this screen just had.
        when (val match = reads.match(userId, found, servings)) {
            is AppResult.Failure -> failed(match.error)
            is AppResult.Success -> {
                currentMatch.value = match.data
                render(found, match.data, servings ?: found.servings)
            }
        }
    }

    private fun render(
        found: Recipe,
        match: PantryMatch?,
        servings: Int = internalState.value.servings,
    ) {
        // All four, as the pantry screen passes them: without the tags there is no language to
        // resolve into, and every lookup misses and renders its identifier instead.
        val resolver =
            LabelResolver(
                terms = units.value,
                ingredients = names.value,
                taxonomies = vocabularies.value,
                languageTags = languageTags,
            )
        internalState.update { current ->
            current.copy(
                title = found.title,
                summary = found.summary,
                totalMinutes = found.totalMinutes,
                servings = servings,
                held = match?.covered.orEmpty().map { it.ingredient.toUi(resolver) },
                missing = match?.missing.orEmpty().map { it.ingredient.toUi(resolver) },
                unverifiable = match?.unverifiable.orEmpty().map { it.toUi(resolver) },
                steps = found.steps,
                isLoading = false,
                error = null,
            )
        }
    }

    /**
     * Every write goes through here, so "one at a time" and "no exception reaches the screen"
     * are stated once instead of three times.
     */
    private fun act(
        // Only the caller knows what its own validation failure means. Read here it would be a
        // guess: two of these actions can fail on the same field for opposite reasons.
        validation: ((AppError.Validation) -> UiText)? = null,
        block: suspend (UserId) -> AppResult<RecipeDetailEvent>?,
    ) {
        val userId = user ?: return
        if (internalState.value.isWorking) return
        internalState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            when (val outcome = block(userId)) {
                null -> Unit
                is AppResult.Failure -> {
                    val message = outcome.error.describe(Res.string.error_unauthorized_action, validation)
                    announce(RecipeDetailEvent.Failed(message))
                }
                is AppResult.Success -> {
                    if (outcome.data is RecipeDetailEvent.Saved) internalState.update { it.copy(isSaved = true) }
                    announce(outcome.data)
                    // Cooking changed the pantry, so the buckets beside it are now stale.
                    if (outcome.data is RecipeDetailEvent.Cooked) refreshMatch(userId)
                }
            }
            internalState.update { it.copy(isWorking = false) }
        }
    }

    private suspend fun announce(event: RecipeDetailEvent) = eventChannel.send(event)

    private suspend fun failed(error: AppError) {
        val message = error.describe(Res.string.error_unauthorized_action)
        internalState.update { it.copy(isLoading = false, isWorking = false, error = message) }
        announce(RecipeDetailEvent.Failed(message))
    }
}
