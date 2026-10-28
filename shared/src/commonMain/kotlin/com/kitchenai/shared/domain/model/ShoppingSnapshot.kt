package com.kitchenai.shared.domain.model

/** One read of the signed-in user's list, for a surface that cannot hold a listener open: a widget. */
data class ShoppingSnapshot(
    val kitchenId: KitchenId,
    val listId: ShoppingListId,
    /** Unchecked lines only, least recently touched first: the order the list screen shows them in. */
    val pending: List<ShoppingItem>,
)
