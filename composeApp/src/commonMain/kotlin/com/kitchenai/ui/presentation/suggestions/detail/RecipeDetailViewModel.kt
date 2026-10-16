package com.kitchenai.ui.presentation.suggestions.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.CookPlan
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.service.PantryCandidateMatcher
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.presentation.common.wordFor
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_missing_ingredients
import com.kitchenai.ui.resources.error_unauthorized_action
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModel(
    private val reads: RecipeDetailReadsDelegate,
    private val writes: RecipeDetailWritesDelegate,
    private val observeKitchen: ObserveKitchenUseCase,
) : ViewModel() {
    private val internalState = MutableStateFlow(RecipeDetailUiState())
    val state: StateFlow<RecipeDetailUiState> = internalState.asStateFlow()

    private val eventChannel = Channel<RecipeDetailEvent>(Channel.BUFFERED)
    val events: Flow<RecipeDetailEvent> = eventChannel.receiveAsFlow()

    // Not exposed and never collected on their own — everything here runs confined to Main, so
    // a plain `var` would be just as safe. Kept as flows only so `.value` reads stay uniform with
    // `internalState`'s; see #147 for whether that uniformity is worth keeping.
    private val names = MutableStateFlow<List<Ingredient>>(emptyList())

    // Every term this recipe's own quantities and tags could point at. Not only units: a tag is
    // a TermRef into whatever taxonomy the catalogue seeded it from, and that is never fixed.
    private val vocabulary = MutableStateFlow<List<Term>>(emptyList())

    // The taxonomies themselves, not only their terms: a term whose language the user does not
    // read falls back to the tag its taxonomy declares, and that tag lives here.
    private val vocabularies = MutableStateFlow<List<Taxonomy>>(emptyList())
    private val recipe = MutableStateFlow<Recipe?>(null)
    private val currentMatch = MutableStateFlow<PantryMatch?>(null)

    // For candidate matching only — PantryMatcher already read the pantry to build the match
    // above, and dropped the snapshot along the way. Never persisted: a candidate a person
    // confirms is a fact about this viewing, not a write to the pantry or the catalogue (#163).
    private val pantry = MutableStateFlow<List<PantryItem>>(emptyList())
    private var pantryKnown = false
    private val confirmedCandidates = MutableStateFlow<Set<PantryItemId>>(emptySet())
    private val loading = MutableStateFlow<Job?>(null)
    private var id: RecipeId? = null
    private var listLabels: Map<String, String> = emptyMap()
    private var languageTags: List<String> = emptyList()
    private var started = false
    private var userId: UserId? = null
    private var kitchenListener: Job? = null

    // The kitchen a user belongs to can change while this screen is open (joining another one),
    // so it is a listener like the pantry itself, not a value captured once at start().
    private val kitchenId = MutableStateFlow<KitchenId?>(null)

    fun start(
        userId: UserId,
        recipeId: RecipeId,
        languageTags: List<String>,
        defaultListName: String,
    ) {
        id = recipeId
        this.userId = userId
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
        viewModelScope.launch {
            kitchenId.filterNotNull().flatMapLatest(reads.pantry::invoke).collect { loaded ->
                val changed = pantryKnown && loaded != pantry.value
                pantryKnown = true
                pantry.value = loaded
                recipe.value?.let { held -> render(held, currentMatch.value) }
                if (changed) refreshMatchOnPantryChange()
            }
        }
        viewModelScope.launch {
            kitchenId.filterNotNull().flatMapLatest(reads.saved::invoke).collect { kept ->
                internalState.update { it.copy(isSaved = kept.any { saved -> saved.id == id }) }
            }
        }
        watchVocabulary()
        watchKitchen(userId, recipeId)
    }

    /**
     * The first kitchen loads the recipe and matches it; a later kitchen (joined mid-visit) only
     * re-matches, since the recipe itself is not kitchen-owned and is already on screen.
     */
    private fun watchKitchen(
        userId: UserId,
        recipeId: RecipeId,
    ) {
        kitchenListener =
            viewModelScope.launch {
                launch {
                    observeKitchen(userId).collect { kitchen ->
                        val firstKitchen = kitchenId.value == null
                        kitchenId.value = kitchen.id
                        if (firstKitchen) load(kitchen.id, recipeId, servings = null) else refreshMatch(kitchen.id)
                    }
                }
                launch { observeKitchen.errors(userId).collect { error -> failed(error) } }
            }
    }

    /**
     * Asks again for what failed to load: the recipe when the kitchen is known, otherwise the kitchen
     * listener itself. A failed re-match keeps the servings the person chose.
     */
    fun retry() {
        val recipeId = id ?: return
        internalState.update { it.copy(isLoading = recipe.value == null, error = null) }
        val kitchen = kitchenId.value
        if (kitchen != null) {
            load(kitchen, recipeId, servings = internalState.value.servings.takeIf { recipe.value != null })
        } else {
            val uid = userId ?: return
            kitchenListener?.cancel()
            watchKitchen(uid, recipeId)
        }
    }

    /** Purely local: nothing is written to the pantry or the catalogue by confirming one. */
    fun confirmCandidate(id: PantryItemId) {
        confirmedCandidates.update { current -> if (id in current) current - id else current + id }
        recipe.value?.let { held -> render(held, currentMatch.value) }
    }

    /**
     * The taxonomies watched are the ones this recipe actually points at, not a fixed set named
     * here: the one the catalogue declares for units, plus whichever taxonomies this recipe's
     * own tags belong to. A tag can come from any vocabulary the catalogue seeds — diets,
     * cuisines, whatever is added later — so the set can only be read off the recipe itself.
     *
     * [flatMapLatest] over the combined id set replaces the whole set of per-taxonomy listeners
     * whenever the taxonomy list or the loaded recipe changes, rather than accumulating one
     * listener per emission.
     */
    private fun watchVocabulary() {
        viewModelScope.launch {
            reads.taxonomies().collect { published -> vocabularies.value = published }
        }
        val watchedIds =
            combine(vocabularies, recipe) { declared, found ->
                val unitTaxonomies = declared.filter { it.purpose == TaxonomyPurpose.UNITS }.map { it.id }
                unitTaxonomies.toSet() + found?.tags.orEmpty().map { it.taxonomy }.toSet()
            }.distinctUntilChanged()
        viewModelScope.launch {
            watchedIds.flatMapLatest(::termsOf).collect { terms ->
                vocabulary.value = terms
                // Suppressed while a match is in flight: matched() sets recipe.value before its
                // own match resolves, and this collector reacts to that same recipe.value — an
                // opportunistic render here would pair the new recipe with an unrelated match.
                // Once the load settles, matched() has already rendered a consistent pair, and
                // this goes back to its real job: late-arriving labels for what is already shown.
                if (loading.value?.isActive != true) {
                    recipe.value?.let { held -> render(held, currentMatch.value) }
                }
            }
        }
    }

    private fun termsOf(ids: Set<TaxonomyId>): Flow<List<Term>> =
        if (ids.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(ids.map { id -> reads.taxonomy(id) }) { loaded -> loaded.toList().flatten() }
        }

    /** Re-scales and re-matches together; neither is useful without the other. */
    fun setServings(servings: Int) {
        if (servings < 1) return
        internalState.update { it.copy(servings = servings) }
        val recipeId = id ?: return
        val kitchen = kitchenId.value ?: return
        load(kitchen, recipeId, servings)
    }

    fun save() =
        act { kitchen ->
            val held = recipe.value ?: return@act null
            writes.save(kitchen, held).map { RecipeDetailEvent.Saved }
        }

    /** Removing twice is not an error (the use case is idempotent), so a stale heart cannot fail here. */
    fun unsave() =
        act { kitchen ->
            val recipeId = id ?: return@act null
            writes.unsave(kitchen, recipeId).map { RecipeDetailEvent.Unsaved }
        }

    // Both writes hand over the recipe in hand rather than its id, for the same reason the read
    // and the match do: a generated dish is in no repository, so re-reading it would fail.
    fun addMissingToList() =
        act { kitchen ->
            val held = recipe.value ?: return@act null
            when (val list = writes.defaultList(kitchen, listLabels)) {
                is AppResult.Failure -> AppResult.Failure(list.error)
                is AppResult.Success ->
                    writes
                        .addMissing(kitchen, list.data, held, internalState.value.servings)
                        .map { summary -> RecipeDetailEvent.AddedToList(summary.added, summary.skipped) }
            }
        }

    /** Opens the confirmation with what a cook would subtract right now; nothing is written yet. */
    fun reviewCook() =
        act { kitchen ->
            val held = recipe.value ?: return@act null
            when (val plan = writes.planCook(kitchen, held, internalState.value.servings)) {
                is AppResult.Failure -> plan
                is AppResult.Success -> {
                    val resolver = resolver()
                    val review =
                        CookReviewUi(
                            deductions = plan.data.deductions.map { it.toUi(resolver) },
                            missing =
                                plan.data.missing
                                    .filter { !it.ingredient.optional }
                                    .map { it.ingredient.toUi(resolver).name },
                        )
                    internalState.update { it.copy(cookReview = review) }
                    null
                }
            }
        }

    fun dismissCookReview() = internalState.update { it.copy(cookReview = null) }

    /**
     * Goes ahead without the missing lines only when the screen said so: a pantry that lost a
     * holding since the review is refused rather than cooked "anyway" behind the reader's back.
     */
    fun cook() {
        val anyway = internalState.value.cooksAnyway
        internalState.update { it.copy(cookReview = null) }
        act(validation = { UiText.of(Res.string.error_missing_ingredients) }) { kitchen ->
            val held = recipe.value ?: return@act null
            writes.cook(kitchen, held, internalState.value.servings, anyway).map { RecipeDetailEvent.Cooked(it) }
        }
    }

    fun undoCook(cooked: CookPlan) =
        act { kitchen -> writes.undoCook(kitchen, cooked).map { RecipeDetailEvent.CookUndone } }

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
        kitchenId: KitchenId,
        recipeId: RecipeId,
        servings: Int?,
    ) {
        matching {
            // The last generation first: a generated dish was never written anywhere else, so
            // the repository would answer NotFound for the only kind of recipe this screen is
            // reached with. A failed local read falls through to the repository, same as a miss.
            val stored = (reads.storedRecipe(recipeId) as? AppResult.Success)?.data
            if (stored != null) {
                matched(kitchenId, stored, servings)
                return@matching
            }
            when (val found = reads.recipe(recipeId)) {
                is AppResult.Failure -> failed(found.error)
                is AppResult.Success -> matched(kitchenId, found.data, servings)
            }
        }
    }

    /**
     * The buckets against the pantry as it is now, from the recipe in hand. Never by id: a
     * generated dish may have left the cache by now, and the repository would answer NotFound
     * for a cook that just succeeded.
     *
     * Reads the servings when it runs rather than when it was queued, so a stepper tap that
     * overlapped the cook is answered for rather than overwritten.
     *
     * Silent when the re-match fails. The pantry has already changed and cannot be put back, so
     * a stale bucket is a smaller lie than blaming somebody for a read they never asked for.
     */
    private fun refreshMatch(kitchenId: KitchenId) {
        val held = recipe.value ?: return
        matching {
            val servings = internalState.value.servings
            val match = reads.match(kitchenId, held, servings)
            if (match is AppResult.Success) {
                currentMatch.value = match.data
                render(held, match.data, servings)
            }
        }
    }

    /**
     * The pantry changed under a dish that is open or retained on the back stack (shopping
     * happens on another tab), so its buckets are stale. Skipped while the first match is
     * still loading: that load reads the pantry itself, and cancelling it here would restart it
     * before the servings it was asked for are on screen.
     *
     * This is also what refreshes after a cook, which changes the pantry like any other writer:
     * a second explicit refresh there would match twice for one change.
     */
    private fun refreshMatchOnPantryChange() {
        val kitchen = kitchenId.value ?: return
        if (internalState.value.isLoading) return
        refreshMatch(kitchen)
    }

    private suspend fun matched(
        kitchenId: KitchenId,
        found: Recipe,
        servings: Int?,
    ) {
        recipe.value = found
        // Matched against the recipe in hand, never re-read by id: a generated dish is not in
        // any repository, and asking for it again is the failure this screen just had.
        when (val match = reads.match(kitchenId, found, servings)) {
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
        val resolver = resolver()
        val candidates = PantryCandidateMatcher.candidatesFor(match?.unverifiable.orEmpty(), pantry.value)
        internalState.update { current ->
            current.copy(
                title = found.title,
                summary = found.summary,
                totalMinutes = found.totalMinutes,
                servings = servings,
                held = match?.covered.orEmpty().map { it.ingredient.toUi(resolver) },
                missing = match?.missing.orEmpty().map { it.ingredient.toUi(resolver) },
                unverifiable =
                    match?.unverifiable.orEmpty().map { line ->
                        line.toUi(resolver, candidates[line].orEmpty(), confirmedCandidates.value)
                    },
                steps = found.steps,
                tags = found.tags.map(resolver::wordFor),
                tagRefs = found.tags,
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
        block: suspend (KitchenId) -> AppResult<RecipeDetailEvent>?,
    ) {
        val kitchen = kitchenId.value ?: return
        if (internalState.value.isWorking) return
        internalState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            when (val outcome = block(kitchen)) {
                null -> Unit
                is AppResult.Failure -> {
                    val message = outcome.error.describe(Res.string.error_unauthorized_action, validation)
                    announce(RecipeDetailEvent.Failed(message))
                }
                is AppResult.Success -> {
                    // Ahead of the saved-recipes listener, which confirms it a moment later.
                    when (outcome.data) {
                        RecipeDetailEvent.Saved -> internalState.update { it.copy(isSaved = true) }
                        RecipeDetailEvent.Unsaved -> internalState.update { it.copy(isSaved = false) }
                        else -> Unit
                    }
                    announce(outcome.data)
                }
            }
            internalState.update { it.copy(isWorking = false) }
        }
    }

    // All four, as the pantry screen passes them: without the tags there is no language to
    // resolve into, and every lookup misses and renders its identifier instead.
    private fun resolver(): LabelResolver =
        LabelResolver(
            terms = vocabulary.value,
            ingredients = names.value,
            taxonomies = vocabularies.value,
            languageTags = languageTags,
        )

    private suspend fun announce(event: RecipeDetailEvent) = eventChannel.send(event)

    private suspend fun failed(error: AppError) {
        val message = error.describe(Res.string.error_unauthorized_action)
        internalState.update { it.copy(isLoading = false, isWorking = false, error = message) }
        announce(RecipeDetailEvent.Failed(message))
    }
}
