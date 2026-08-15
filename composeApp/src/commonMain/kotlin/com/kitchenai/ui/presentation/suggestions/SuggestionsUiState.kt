package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.domain.model.RecipeId
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
    val isGenerating: Boolean = false,
    val hasGenerated: Boolean = false,
    val error: UiText? = null,
    val options: SuggestionOptionsUi = SuggestionOptionsUi(),
)

/** One card. Everything here is already resolved to words; the screen looks nothing up. */
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
)

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
)
