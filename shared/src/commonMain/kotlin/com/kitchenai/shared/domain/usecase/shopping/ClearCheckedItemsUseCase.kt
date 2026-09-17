package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract

/** Drops every checked line at once, after the shopping is done. */
class ClearCheckedItemsUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        listId: ShoppingListId,
    ): AppResult<Unit> = shoppingItems.removeCheckedItems(kitchenId, listId)
}
