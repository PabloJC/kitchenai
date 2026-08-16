package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Streams the items of a list in the order they are shown in. */
class ObserveShoppingItems(
    private val shoppingItems: ShoppingItemRepositoryContract,
) {
    operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>> = shoppingItems.observeItems(userId, listId).map(::order)

    /** The listener's failures, collected alongside the stream above. */
    fun errors(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<AppError> = shoppingItems.itemErrors(userId, listId)

    // Unchecked first and least recently touched first inside each group: ticking a line sends
    // it to the bottom without reshuffling the lines above it in a supermarket aisle.
    private fun order(items: List<ShoppingItem>): List<ShoppingItem> =
        items.sortedWith(compareBy({ it.checked }, { it.updatedAt }))
}
