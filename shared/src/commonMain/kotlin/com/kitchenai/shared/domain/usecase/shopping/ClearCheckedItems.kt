package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingItemPort

/** Drops every checked line at once, after the shopping is done. */
class ClearCheckedItems(
    private val shoppingItems: ShoppingItemPort,
) {
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<Unit> = shoppingItems.removeCheckedItems(userId, listId)
}
