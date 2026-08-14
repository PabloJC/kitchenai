package com.kitchenai.ui.presentation.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.recipe.SuggestRecipes
import com.kitchenai.ui.presentation.common.LabelResolver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Suggestions, on demand.
 *
 * Nothing runs in `init`, and that is the one design decision on this screen. A model call
 * costs money and takes the better part of a minute; an automatic one on screen entry would
 * spend both every time somebody touched the tab.
 */
class SuggestionsViewModel(
    private val suggestRecipes: SuggestRecipes,
    private val cache: SuggestionCache,
    private val observeIngredients: ObserveIngredients,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val internalState = MutableStateFlow(SuggestionsUiState())
    val state: StateFlow<SuggestionsUiState> = internalState.asStateFlow()

    private val eventChannel = Channel<SuggestionsEvent>(Channel.BUFFERED)
    val events: Flow<SuggestionsEvent> = eventChannel.receiveAsFlow()

    // The catalogue names the lines a suggestion carries. It streams because the screen may be
    // open before the catalogue has arrived, and a suggestion generated later must still resolve.
    private val ingredients = MutableStateFlow<List<Ingredient>>(emptyList())
    private var user: UserId? = null
    private var started = false

    /** Idempotent: a configuration change composes the screen again and must not double the listener. */
    fun start(userId: UserId) {
        user = userId
        if (started) return
        started = true
        viewModelScope.launch(dispatchers.default) {
            observeIngredients().collect { loaded -> ingredients.value = loaded }
        }
    }

    fun setMaxResults(value: Int) = editOptions { it.copy(maxResults = value) }

    fun setMaxMinutes(value: Int?) = editOptions { it.copy(maxMinutes = value) }

    fun setUseOnlyPantry(value: Boolean) = editOptions { it.copy(useOnlyPantry = value) }

    /**
     * A second tap while one is in flight is ignored rather than queued: two runs would bill
     * twice for an answer only one of which could be shown.
     */
    fun generate() {
        val userId = user ?: return
        if (internalState.value.isGenerating) return
        internalState.update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch(dispatchers.default) {
            when (val answered = suggestRecipes(userId, internalState.value.options.toDomain())) {
                is AppResult.Failure -> fail(answered.error)
                is AppResult.Success -> show(answered.data)
            }
        }
    }

    private fun editOptions(block: (SuggestionOptionsUi) -> SuggestionOptionsUi) =
        internalState.update { current -> current.copy(options = block(current.options)) }

    private fun show(suggestions: List<RecipeSuggestion>) {
        val resolver = LabelResolver(ingredients = ingredients.value)
        // Before the state, so a card is never tappable before the dish behind it is reachable.
        cache.put(suggestions.map { it.recipe })
        internalState.update { current ->
            current.copy(
                suggestions = suggestions.map { suggestion -> suggestion.toUi(resolver) },
                isGenerating = false,
                hasGenerated = true,
                error = null,
            )
        }
    }

    private suspend fun fail(error: AppError) {
        val message = error.describe()
        // Both: the banner survives a rotation, the event does not, and a failure the user
        // scrolled past still has to be findable.
        internalState.update { it.copy(isGenerating = false, hasGenerated = true, error = message) }
        eventChannel.send(SuggestionsEvent.Failed(message))
    }
}

private fun SuggestionOptionsUi.toDomain(): SuggestionOptions =
    SuggestionOptions(maxResults = maxResults, maxMinutes = maxMinutes, useOnlyPantry = useOnlyPantry)

private fun RecipeSuggestion.toUi(resolver: LabelResolver): SuggestionUi {
    val held = match.covered.count { !it.ingredient.optional }
    val short = match.missing.count { !it.ingredient.optional }
    return SuggestionUi(
        id = recipe.id,
        title = recipe.title,
        summary = recipe.summary,
        totalMinutes = recipe.totalMinutes,
        coverage = match.coverage,
        heldCount = held,
        totalCount = held + short,
        missing = match.missing.map { it.ingredient.name(resolver) },
        unverifiable = match.unverifiable.map { line -> line.name(resolver) },
        fromAgent = source is RecipeSource.Agent,
    )
}

/** Free text is already its own name; a catalogue line falls back to its identifier. */
private fun RecipeIngredient.name(resolver: LabelResolver): String {
    freeText?.let { return it }
    val id = ingredient ?: return ""
    return resolver.label(id) ?: id.value
}

/**
 * `Unauthorized` gets its own sentence: it means App Check or sign-in is wrong, and "no
 * connection" would send someone to restart their router over a problem inside the app.
 */
private fun AppError.describe(): String =
    when (this) {
        is AppError.Network -> "No connection"
        is AppError.Unauthorized -> "This app could not prove who it is, so suggestions are unavailable"
        is AppError.NotFound -> "Cannot find $resource"
        is AppError.Validation -> "Invalid $field: $reason"
        is AppError.Unknown -> "Something went wrong"
    }
