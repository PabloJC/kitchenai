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
    // A list, not a map grouped by taxonomy: the domain type is ordered and grouping loses that
    // order whenever preferences interleave taxonomies.
    val preferences: List<TermRefDto> = emptyList(),
    val avoidedIngredients: List<String> = emptyList(),
    val updatedAtMillis: Long? = null,
)
