package com.kitchenai.shared.data.remote.agent.dto

import kotlinx.serialization.Serializable

/**
 * What travels to the function. Every field is an identifier or a number: there is no field in
 * which a sentence can leave this device, which is what keeps prompt assembly on the server.
 *
 * [requestId] is the client's, so a retry is recognisable as the same question.
 */
@Serializable
data class SuggestRequestDto(
    val schemaVersion: Int,
    val requestId: String,
    val capability: String,
    val languageTags: List<String>,
    val servings: Int,
    val options: SuggestOptionsDto,
    val constraints: List<ConstraintDto>,
    val preferences: List<TermRefDto>,
    val avoidedIngredients: List<String>,
    val pantry: List<PantryEntryDto>,
)

@Serializable
data class SuggestOptionsDto(
    val maxResults: Int,
    val maxMinutes: Int?,
    val useOnlyPantry: Boolean,
)

@Serializable
data class ConstraintDto(
    val taxonomy: String,
    val term: String,
    val strength: String,
)

@Serializable
data class TermRefDto(
    val taxonomy: String,
    val term: String,
)

/** A holding as the agent sees it: what, how much, and whether it is about to be lost. */
@Serializable
data class PantryEntryDto(
    val ingredientId: String,
    val amount: Double,
    val unitTaxonomy: String?,
    val unitTerm: String?,
    val expiringSoon: Boolean,
)
