package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider

/**
 * Assigns the quantity of a line, or clears it with null. It replaces rather than tops up: a hand
 * edit states the amount the person means. A line needs an amount to move to the pantry.
 */
class SetShoppingItemQuantityUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
        quantity: Quantity?,
    ): AppResult<Unit> {
        if (quantity != null && !(quantity.amount > 0.0 && quantity.amount.isFinite())) {
            return AppResult.Failure(AppError.Validation("amount", "must be greater than zero"))
        }
        val snapshot = shoppingItems.getItems(kitchenId, listId)
        if (snapshot is AppResult.Failure) return snapshot
        val item = (snapshot as AppResult.Success).data.firstOrNull { it.id == itemId }
        if (item == null) return AppResult.Failure(AppError.NotFound("shoppingItem"))
        val updated = item.copy(quantity = quantity, updatedAt = time.now())
        return shoppingItems.upsertItems(kitchenId, listId, listOf(updated))
    }
}
