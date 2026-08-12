package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `users/{uid}` document. The identifier is the document id and is never repeated inside
 * the payload.
 *
 * Everything is optional on the wire: a document written by an older client is a decoding
 * decision for the mapper, which reports it, not a deserialiser crash the caller cannot map.
 */
@Serializable
data class UserProfileDto(
    val displayName: String? = null,
    val languageTags: List<String> = emptyList(),
    val household: HouseholdDto? = null,
    val constraints: List<DietaryConstraintDto> = emptyList(),
    // Grouped by taxonomy so a preference query stays a single `array-contains` on one field.
    val preferences: Map<String, List<String>> = emptyMap(),
    val avoidedIngredients: List<String> = emptyList(),
    val updatedAtMillis: Long? = null,
)
