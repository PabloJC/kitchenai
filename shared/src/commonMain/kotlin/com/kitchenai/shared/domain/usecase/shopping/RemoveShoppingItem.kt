package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract

/** Removes one line. Removing an already removed line is not an error. */
class RemoveShoppingItem(
    private val shoppingItems: ShoppingItemRepositoryContract,
) {
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
    ): AppResult<Unit> = shoppingItems.removeItem(userId, listId, itemId)
}
