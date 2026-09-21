package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `kitchens/{kitchenId}/shoppingLists/{listId}` document.
 *
 * The key set is pinned by `isValidShoppingList` in `firebase/firestore.rules`: a field added
 * here and not there is denied at write time. [ownerId] names which member created the list —
 * no longer redundant now that the path only says which kitchen owns it, not which member does.
 */
@Serializable
data class ShoppingListDto(
    val ownerId: String,
    val labels: Map<String, String> = emptyMap(),
    val updatedAtMillis: Long,
)
