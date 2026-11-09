package com.kitchenai.shared.data.remote.agent.dto

import kotlinx.serialization.Serializable

/**
 * What travels to the function. Every field is an identifier or a number, except the bounded
 * names in [freeTextPantry] (#393): no field carries a sentence, so prompt assembly stays on the server.
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
    // Optional on the wire: a function from before #393 ignores the key.
    val freeTextPantry: List<PantryFreeTextEntryDto> = emptyList(),
)

@Serializable
data class SuggestOptionsDto(
    val maxResults: Int,
    val maxMinutes: Int?,
    val useOnlyPantry: Boolean,
    val useExpiringSoon: Boolean = false,
    val mealType: String? = null,
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

/** A holding the catalogue does not know: a sanitised name, how much, and whether it is about to be lost. */
@Serializable
data class PantryFreeTextEntryDto(
    val name: String,
    val amount: Double,
    val unitTaxonomy: String?,
    val unitTerm: String?,
    val expiringSoon: Boolean,
)
