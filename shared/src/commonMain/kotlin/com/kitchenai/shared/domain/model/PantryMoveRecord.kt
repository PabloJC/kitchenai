package com.kitchenai.shared.domain.model

/**
 * What one move to the pantry changed, kept so it can be put back: the shopping [lines] it took
 * off [listId], each pantry row it merged into as it was [before], and the rows it [created].
 */
data class PantryMoveRecord(
    val listId: ShoppingListId,
    val lines: List<ShoppingItem>,
    val before: List<PantryItem>,
    val created: List<PantryItemId>,
)
