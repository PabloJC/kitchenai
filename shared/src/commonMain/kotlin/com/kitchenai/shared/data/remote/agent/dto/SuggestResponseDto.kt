package com.kitchenai.shared.data.remote.agent.dto

import kotlinx.serialization.Serializable

/**
 * What comes back. Nothing here is trusted: this is the shape the payload must have, not a
 * promise about its contents. `SuggestionValidator` is what decides which of it becomes a
 * [com.kitchenai.shared.domain.model.Recipe].
 *
 * Every optional field is optional because a model can omit it, not because it is meaningful
 * when absent.
 */
@Serializable
data class SuggestResponseDto(
    val schemaVersion: Int,
    val agentId: String,
    val modelId: String,
    val suggestions: List<SuggestedRecipeDto> = emptyList(),
)

@Serializable
data class SuggestedRecipeDto(
    val title: String? = null,
    val summary: String? = null,
    val servings: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<SuggestedIngredientDto> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<SuggestedTagDto> = emptyList(),
)

/** A line is either a catalogue pointer or free text; the validator rejects both and neither. */
@Serializable
data class SuggestedIngredientDto(
    val ingredientId: String? = null,
    val freeText: String? = null,
    val amount: Double? = null,
    val unitTaxonomy: String? = null,
    val unitTerm: String? = null,
    val optional: Boolean = false,
)

@Serializable
data class SuggestedTagDto(
    val taxonomy: String? = null,
    val term: String? = null,
)
