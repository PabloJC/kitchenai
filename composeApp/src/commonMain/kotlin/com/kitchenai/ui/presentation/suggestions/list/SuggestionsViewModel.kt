package com.kitchenai.ui.presentation.suggestions.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetStoredSuggestionsUseCase
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantryUseCase
import com.kitchenai.shared.domain.usecase.recipe.ObserveSavedRecipesUseCase
import com.kitchenai.shared.domain.usecase.recipe.StoreSuggestionsUseCase
import com.kitchenai.shared.domain.usecase.recipe.SuggestRecipesUseCase
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_suggestions
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
    private val suggestRecipes: SuggestRecipesUseCase,
    private val getStoredSuggestions: GetStoredSuggestionsUseCase,
    private val storeSuggestions: StoreSuggestionsUseCase,
    private val observeIngredients: ObserveIngredientsUseCase,
    private val observeSavedRecipes: ObserveSavedRecipesUseCase,
    private val matchRecipe: MatchRecipeAgainstPantryUseCase,
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
        viewModelScope.launch {
            observeIngredients().collect { loaded ->
                ingredients.value = loaded
                // The stored set can be on screen already, showing catalogue ids where the
                // resolver had nothing yet: a local read is near-instant and does not wait for
                // this listener the way the 20-60s model call used to.
                reresolve()
            }
        }
        viewModelScope.launch {
            observeSavedRecipes(userId).collect { recipes -> matchSaved(userId, recipes) }
        }
        viewModelScope.launch {
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
     *
     * Safe against the launch auto-generate racing a tap on the button precisely because neither
     * this nor [start] ever names a dispatcher: both run confined to [viewModelScope]'s own Main,
     * so the guard below and the auto-generate's call to this are never truly concurrent.
     */
    fun generate() {
        val userId = user ?: return
        if (internalState.value.isGenerating) return
        internalState.update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch {
            when (val answered = suggestRecipes(userId, languageTags, internalState.value.options.toDomain())) {
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
        current = suggestions
        reresolve()
    }

    /**
     * A saved recipe carries no match of its own — matching is what a suggestion's orchestrator
     * stamps on the way out, and a save just keeps the [Recipe]. Computed here the same way
     * [RecipeDetailViewModel] does it, so the coverage bar means the same thing on every card.
     *
     * A recipe whose own match fails is dropped from the section rather than shown broken or
     * blamed with a banner: one card silently missing costs less than a section-wide error over
     * what is otherwise a working list, and the pantry listener behind [matchRecipe] retries
     * itself on its own.
     */
    private suspend fun matchSaved(
        userId: UserId,
        recipes: List<Recipe>,
    ) {
        currentSaved =
            recipes.mapNotNull { recipe ->
                val match = matchRecipe(userId, recipe) as? AppResult.Success ?: return@mapNotNull null
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
        internalState.update { state ->
            state.copy(
                suggestions = current.map { suggestion -> suggestion.toUi(resolver) },
                savedRecipes = currentSaved.map { suggestion -> suggestion.toUi(resolver) },
            )
        }
    }

    private suspend fun fail(error: AppError) {
        val message = error.describe(Res.string.error_unauthorized_suggestions)
        // Both: the banner survives a rotation, the event does not, and a failure the user
        // scrolled past still has to be findable.
        internalState.update { it.copy(isGenerating = false, hasGenerated = true, error = message) }
        eventChannel.send(SuggestionsEvent.Failed(message))
    }
}
