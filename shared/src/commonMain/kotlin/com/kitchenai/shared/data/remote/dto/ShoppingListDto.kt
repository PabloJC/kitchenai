package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `users/{uid}/shoppingLists/{listId}` document.
 *
 * The key set is pinned by `isValidShoppingList` in `firebase/firestore.rules`: a field added
 * here and not there is denied at write time. [ownerId] is redundant under `users/{uid}` and is
 * kept because a list read outside its path still has to name its owner.
 */
@Serializable
data class ShoppingListDto(
    val ownerId: String,
    val labels: Map<String, String> = emptyMap(),
    val updatedAtMillis: Long,
)
