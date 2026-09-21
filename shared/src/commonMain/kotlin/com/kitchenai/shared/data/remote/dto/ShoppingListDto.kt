package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `kitchens/{kitchenId}/shoppingLists/{listId}` document.
 *
 * The key set is pinned by `isValidShoppingList` in `firebase/firestore.rules`: a field added
 * here and not there is denied at write time. No owner/kitchen id field: the path already says
 * which kitchen owns it, and [com.kitchenai.shared.data.mapper.toDomain] takes the kitchen id as
 * a parameter from the caller, who already knows it, rather than duplicating it in the document.
 */
@Serializable
data class ShoppingListDto(
    val labels: Map<String, String> = emptyMap(),
    val updatedAtMillis: Long,
)
