package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `users/{uid}/shoppingLists/{listId}/items/{itemId}` document.
 *
 * Flat rather than nested, like the pantry: a nested quantity buys nothing and costs a harder
 * index definition. Exactly one of [ingredientId] and [freeText] is set — the invariant the
 * domain factory and the Firestore rules both enforce. The identifier is the document id.
 */
@Serializable
data class ShoppingItemDto(
    val ingredientId: String? = null,
    val freeText: String? = null,
    val amount: Double? = null,
    val unitTaxonomy: String? = null,
    val unitTerm: String? = null,
    val checked: Boolean = false,
    val sourceRecipeId: String? = null,
    val updatedAtMillis: Long,
)
