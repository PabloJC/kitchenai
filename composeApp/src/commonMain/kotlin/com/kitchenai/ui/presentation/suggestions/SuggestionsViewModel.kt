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
import com.kitchenai.shared.domain.usecase.recipe.GetStoredSuggestions
import com.kitchenai.shared.domain.usecase.recipe.StoreSuggestions
import com.kitchenai.shared.domain.usecase.recipe.SuggestRecipes
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_invalid_field
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_not_found
import com.kitchenai.ui.resources.error_timeout
import com.kitchenai.ui.resources.error_unauthorized_suggestions
import com.kitchenai.ui.resources.error_unknown
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class SuggestionsViewModel(
    private val suggestRecipes: SuggestRecipes,
    private val getStoredSuggestions: GetStoredSuggestions,
    private val storeSuggestions: StoreSuggestions,
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
    private var languageTags: List<String> = emptyList()
    private var started = false

    /** Idempotent: a configuration change composes the screen again and must not double the listener or the launch. */
    fun start(
        userId: UserId,
        languageTags: List<String>,
    ) {
        user = userId
        this.languageTags = languageTags
        if (started) return
        started = true
        viewModelScope.launch(dispatchers.default) {
            observeIngredients().collect { loaded -> ingredients.value = loaded }
        }
        viewModelScope.launch(dispatchers.default) {
            showStored(userId)
            generate()
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
                is AppResult.Success -> generated(answered.data)
            }
        }
    }

    private fun editOptions(block: (SuggestionOptionsUi) -> SuggestionOptionsUi) =
        internalState.update { current -> current.copy(options = block(current.options)) }

    /** What survived the last launch, shown while this one has nothing of its own yet. */
    private suspend fun showStored(userId: UserId) {
        val stored = (getStoredSuggestions(userId) as? AppResult.Success)?.data
        if (stored.isNullOrEmpty()) return
        render(stored)
    }

    /** A new generation replaces what was stored, on screen and on disk. */
    private suspend fun generated(suggestions: List<RecipeSuggestion>) {
        // The write's own failure does not undo a run the user already sees the result of: it
        // only costs the next launch its head start.
        storeSuggestions(suggestions.map { it.recipe })
        render(suggestions)
        internalState.update { it.copy(isGenerating = false, hasGenerated = true, error = null) }
    }

    private fun render(suggestions: List<RecipeSuggestion>) {
        // With no tags the resolve chain is empty, every lookup misses, and a card names a
        // catalogue ingredient by its identifier. The card carries no quantities, so terms and
        // taxonomies are not needed here — only a language to answer in.
        val resolver = LabelResolver(ingredients = ingredients.value, languageTags = languageTags)
        internalState.update { current ->
            current.copy(suggestions = suggestions.map { suggestion -> suggestion.toUi(resolver) })
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
        // Both ids travel as the response reported them; neither is named anywhere in this module.
        provenance = (source as? RecipeSource.Agent)?.let { ProvenanceUi(it.agentId.value, it.modelId) },
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
private fun AppError.describe(): UiText =
    when (this) {
        is AppError.Network -> UiText.of(Res.string.error_no_connection)
        is AppError.Timeout -> UiText.of(Res.string.error_timeout)
        is AppError.Unauthorized -> UiText.of(Res.string.error_unauthorized_suggestions)
        is AppError.NotFound -> UiText.of(Res.string.error_not_found, resource)
        is AppError.Validation -> UiText.of(Res.string.error_invalid_field, field, reason)
        is AppError.Unknown -> UiText.of(Res.string.error_unknown)
    }
