package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.domain.model.MealType
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.UiText

/**
 * The suggestions screen's one state.
 *
 * [hasGenerated] is not `suggestions.isEmpty()`: a run that legitimately found nothing has to
 * read differently from one that has not happened yet, and the difference is the whole first
 * screen a new user sees.
 */
data class SuggestionsUiState(
    val suggestions: List<SuggestionUi> = emptyList(),
    /** Every kept recipe, newest first: its own view, a save is a standing choice and not this session's answer. */
    val savedRecipes: List<SuggestionUi> = emptyList(),
    /** Which of the two views is on: the ideas feed or the saved recipes. */
    val showingSaved: Boolean = false,
    val savedQuery: String = "",
    /** [savedRecipes] narrowed by [savedQuery], what can be cooked now first and newest first within each. */
    val visibleSaved: List<SuggestionUi> = emptyList(),
    val isGenerating: Boolean = false,
    val hasGenerated: Boolean = false,
    /** Nothing held, so nothing ran on arrival: the empty state offers the pantry instead. */
    val isPantryEmpty: Boolean = false,
    val error: UiText? = null,
    /** New ideas need the model, and the model needs the network: offline the button waits. */
    val isOnline: Boolean = true,
    val options: SuggestionOptionsUi = SuggestionOptionsUi(),
    /** What the results on screen were generated with; null until a run lands. */
    val appliedOptions: SuggestionOptionsUi? = null,
    /** The card whose missing ingredients are being written to the list, so a second tap is ignored. */
    val addingMissingFor: RecipeId? = null,
) {
    /** Never auto-runs: each run costs a model call, so a changed filter only offers one. */
    val optionsChanged: Boolean get() = appliedOptions != null && appliedOptions != options

    /** A handful scans by eye; past that a search earns its place. */
    val offersSavedSearch: Boolean get() = savedRecipes.size > SAVED_SEARCH_THRESHOLD
}

private const val SAVED_SEARCH_THRESHOLD = 8

/**
 * One card. Every word here is already resolved; the screen looks no label up. [tagRefs] is the
 * one exception — it feeds `RecipeImage`'s own lookup, not text on screen.
 */
data class SuggestionUi(
    val id: RecipeId,
    val title: String,
    val summary: String?,
    val totalMinutes: Int?,
    val coverage: Float,
    val heldCount: Int,
    val totalCount: Int,
    val missing: List<String>,
    /** Lines the pantry can say nothing about. Never folded into [missing]: that would be a lie. */
    val unverifiable: List<String>,
    /** Null for anything not generated: a catalogue dish has nobody to attribute it to. */
    val provenance: ProvenanceUi?,
    /** Unresolved, unlike everything above: `RecipeImage` reads it itself, the screen does not. */
    val tagRefs: List<TermRef> = emptyList(),
    /** Someone in the kitchen avoids something in it: kept from before they joined, or saved by another member. */
    val conflictsWithHousehold: Boolean = false,
) {
    val shownMissing: List<String> get() = missing.take(MAX_SHOWN_MISSING)
    val hiddenMissingCount: Int get() = missing.size - shownMissing.size
}

private const val MAX_SHOWN_MISSING = 3

/**
 * Who wrote a suggestion, as the response reported it. Both halves, not a boolean: support
 * cannot chase a bad dish knowing only that some model produced it.
 */
data class ProvenanceUi(
    val agentId: String,
    val modelId: String,
)

data class SuggestionOptionsUi(
    val maxResults: Int = 5,
    val maxMinutes: Int? = null,
    val useOnlyPantry: Boolean = false,
    val useExpiringSoon: Boolean = false,
    /** Null is any meal; one chip at most is on. */
    val mealType: MealType? = null,
)
