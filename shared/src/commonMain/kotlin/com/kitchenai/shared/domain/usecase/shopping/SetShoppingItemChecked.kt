package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider

/**
 * Assigns the checked state of a line. Deliberately not a toggle, which is what a caller will
 * miss: two devices ticking the same line write the same value and converge, whereas two
 * inversions would cancel each other out and leave the line unchecked.
 */
class SetShoppingItemChecked(
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
        checked: Boolean,
    ): AppResult<Unit> {
        val snapshot = shoppingItems.getItems(userId, listId)
        if (snapshot is AppResult.Failure) return snapshot
        // The current state is read to rebuild the document, never to derive the new value.
        val item = (snapshot as AppResult.Success).data.firstOrNull { it.id == itemId }
        if (item == null) return AppResult.Failure(AppError.NotFound("shoppingItem"))
        val updated = item.copy(checked = checked, updatedAt = time.now())
        return shoppingItems.upsertItems(userId, listId, listOf(updated))
    }
}
