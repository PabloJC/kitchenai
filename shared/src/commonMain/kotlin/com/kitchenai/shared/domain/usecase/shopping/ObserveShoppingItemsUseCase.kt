package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Streams the items of a list in the order they are shown in. */
class ObserveShoppingItemsUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
) {
    operator fun invoke(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>> = shoppingItems.observeItems(kitchenId, listId).map(::order)

    /** The listener's failures, collected alongside the stream above. */
    fun errors(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): Flow<AppError> = shoppingItems.itemErrors(kitchenId, listId)

    // Unchecked first and least recently touched first inside each group: ticking a line sends
    // it to the bottom without reshuffling the lines above it in a supermarket aisle.
    private fun order(items: List<ShoppingItem>): List<ShoppingItem> =
        items.sortedWith(compareBy({ it.checked }, { it.updatedAt }))
}
