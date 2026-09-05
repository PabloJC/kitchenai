package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.MovedToPantrySummary
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.AddPantryItemUseCase

/**
 * Moves what is ticked in the cart into the pantry — the same claim a checked line and a pantry
 * row both make, closed in one action instead of typed twice.
 *
 * A line with no stated amount stays on the list rather than inventing one: "milk" with no
 * quantity is a normal shopping line and not a normal pantry row.
 *
 * Every pantry write for the lines that do move is attempted before any of them leaves the
 * list, so a write failing partway leaves the list exactly as it was rather than
 * moved-but-not-removed.
 */
class MoveCheckedItemsToPantryUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val addToPantry: AddPantryItemUseCase,
) {
    suspend operator fun invoke(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<MovedToPantrySummary> {
        val current = shoppingItems.getItems(userId, listId)
        if (current is AppResult.Failure) return current
        val checked = (current as AppResult.Success).data.filter { it.checked }
        val withQuantity = checked.mapNotNull { item -> item.quantity?.let { quantity -> item to quantity } }
        val skipped = checked.size - withQuantity.size
        if (withQuantity.isEmpty()) return AppResult.Success(MovedToPantrySummary(0, skipped))
        for ((item, quantity) in withQuantity) {
            val added = add(userId, item, quantity)
            if (added is AppResult.Failure) return added
        }
        return shoppingItems
            .removeItems(userId, listId, withQuantity.map { (item, _) -> item.id })
            .map { MovedToPantrySummary(withQuantity.size, skipped) }
    }

    private suspend fun add(
        userId: UserId,
        item: ShoppingItem,
        quantity: Quantity,
    ) = addToPantry(userId, item.ingredient, item.freeText, quantity, location = null, expiresAt = null)
}
