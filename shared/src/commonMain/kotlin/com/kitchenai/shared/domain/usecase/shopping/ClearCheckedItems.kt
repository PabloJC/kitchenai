package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingListPort

/** Drops every checked line at once, after the shopping is done. */
class ClearCheckedItems(
    private val shoppingList: ShoppingListPort,
) {
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<Unit> = shoppingList.removeCheckedItems(userId, listId)
}
