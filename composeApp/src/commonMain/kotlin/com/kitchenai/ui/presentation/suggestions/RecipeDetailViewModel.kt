package com.kitchenai.ui.presentation.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.format.formatQuantity
import com.kitchenai.ui.presentation.common.LabelResolver
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
    private val reads: RecipeDetailReads,
    private val writes: RecipeDetailWrites,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val internalState = MutableStateFlow(RecipeDetailUiState())
    val state: StateFlow<RecipeDetailUiState> = internalState.asStateFlow()

    private val eventChannel = Channel<RecipeDetailEvent>(Channel.BUFFERED)
    val events: Flow<RecipeDetailEvent> = eventChannel.receiveAsFlow()

    private val names = MutableStateFlow<List<Ingredient>>(emptyList())
    private val units = MutableStateFlow<List<Term>>(emptyList())
    private var recipe: Recipe? = null
    private var user: UserId? = null
    private var id: RecipeId? = null
    private var started = false

    fun start(
        userId: UserId,
        recipeId: RecipeId,
    ) {
        user = userId
        id = recipeId
        if (started) return
        started = true
        viewModelScope.launch(dispatchers.default) {
            reads.ingredients().collect { loaded ->
                names.value = loaded
                recipe?.let { held -> render(held, currentMatch) }
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
        viewModelScope.launch(dispatchers.default) {
            // collectLatest and coroutineScope together: a second emission of the taxonomy list
            // must replace the per-taxonomy collectors, not add to them. Plain collect leaves
            // the previous ones running, so one unit ends up with a listener per emission.
            reads.taxonomies().collectLatest { published ->
                coroutineScope {
                    published.filter { it.purpose == TaxonomyPurpose.UNITS }.forEach { unit ->
                        launch {
                            reads.taxonomy(unit.id).collect { terms ->
                                units.value = terms
                                recipe?.let { held -> render(held, currentMatch) }
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
            val held = recipe ?: return@act null
            writes.save(userId, held).map { RecipeDetailEvent.Saved }
        }

    // Both writes hand over the recipe in hand rather than its id, for the same reason the read
    // and the match do: a generated dish is in no repository, so re-reading it would fail.
    fun addMissingToList() =
        act { userId ->
            val held = recipe ?: return@act null
            when (val list = writes.defaultList(userId, emptyMap())) {
                is AppResult.Failure -> AppResult.Failure(list.error)
                is AppResult.Success ->
                    writes
                        .addMissing(userId, list.data, held, internalState.value.servings)
                        .map { summary -> RecipeDetailEvent.AddedToList(summary.added, summary.skipped) }
            }
        }

    fun cook() =
        act { userId ->
            val held = recipe ?: return@act null
            writes.cook(userId, held, internalState.value.servings).map { RecipeDetailEvent.Cooked }
        }

    private var currentMatch: PantryMatch? = null

    private fun load(
        recipeId: RecipeId,
        servings: Int?,
    ) {
        val userId = user ?: return
        viewModelScope.launch(dispatchers.default) {
            // The cache first: a generated dish was never written anywhere, so the repository
            // would answer NotFound for the only kind of recipe this screen is reached with.
            val cached = reads.cache[recipeId]
            if (cached != null) {
                matched(userId, cached, servings)
                return@launch
            }
            when (val found = reads.recipe(recipeId)) {
                is AppResult.Failure -> failed(found.error)
                is AppResult.Success -> matched(userId, found.data, servings)
            }
        }
    }

    private suspend fun matched(
        userId: UserId,
        found: Recipe,
        servings: Int?,
    ) {
        recipe = found
        // Matched against the recipe in hand, never re-read by id: a generated dish is not in
        // any repository, and asking for it again is the failure this screen just had.
        when (val match = reads.match(userId, found, servings)) {
            is AppResult.Failure -> failed(match.error)
            is AppResult.Success -> {
                currentMatch = match.data
                render(found, match.data, servings ?: found.servings)
            }
        }
    }

    private fun render(
        found: Recipe,
        match: PantryMatch?,
        servings: Int = internalState.value.servings,
    ) {
        val resolver = LabelResolver(terms = units.value, ingredients = names.value)
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
    private fun act(block: suspend (UserId) -> AppResult<RecipeDetailEvent>?) {
        val userId = user ?: return
        if (internalState.value.isWorking) return
        internalState.update { it.copy(isWorking = true) }
        viewModelScope.launch(dispatchers.default) {
            when (val outcome = block(userId)) {
                null -> Unit
                is AppResult.Failure -> announce(RecipeDetailEvent.Failed(outcome.error.describe()))
                is AppResult.Success -> {
                    if (outcome.data is RecipeDetailEvent.Saved) internalState.update { it.copy(isSaved = true) }
                    announce(outcome.data)
                    // Cooking changed the pantry, so the buckets beside it are now stale.
                    if (outcome.data is RecipeDetailEvent.Cooked) id?.let { load(it, internalState.value.servings) }
                }
            }
            internalState.update { it.copy(isWorking = false) }
        }
    }

    private suspend fun announce(event: RecipeDetailEvent) = eventChannel.send(event)

    private suspend fun failed(error: AppError) {
        internalState.update { it.copy(isLoading = false, isWorking = false, error = error.describe()) }
        announce(RecipeDetailEvent.Failed(error.describe()))
    }
}

private fun RecipeIngredient.toUi(resolver: LabelResolver): IngredientLineUi =
    IngredientLineUi(
        name = freeText ?: ingredient?.let { resolver.label(it) ?: it.value }.orEmpty(),
        quantity = quantity?.let { held -> formatQuantity(held.amount, held.unit?.let(resolver::label)) },
        optional = optional,
    )

/**
 * A cook refused for missing ingredients is not a failure to apologise for: it is the answer,
 * and the screen already lists which ones.
 */
private fun AppError.describe(): String =
    when (this) {
        is AppError.Network -> "No connection"
        is AppError.Unauthorized -> "This account is not allowed to do that"
        is AppError.NotFound -> "Cannot find $resource"
        is AppError.Validation -> "You are missing ingredients for this"
        is AppError.Unknown -> "Something went wrong"
    }
