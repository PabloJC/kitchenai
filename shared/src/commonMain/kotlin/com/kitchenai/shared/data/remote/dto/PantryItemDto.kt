package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `users/{uid}/pantry/{itemId}` document.
 *
 * Flat rather than nested: nested objects make partial updates and index definitions harder for
 * no gain here. The identifier is the document id and is never duplicated inside the payload.
 */
@Serializable
data class PantryItemDto(
    val ingredientId: String,
    val amount: Double,
    val unitTaxonomy: String? = null,
    val unitTerm: String? = null,
    val locationTaxonomy: String? = null,
    val locationTerm: String? = null,
    val expiresAtMillis: Long? = null,
    val updatedAtMillis: Long,
)
