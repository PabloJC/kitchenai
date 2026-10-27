package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryMoveRecord
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingItemRepositoryContract

/**
 * Puts a move to the pantry back: the lines return to the list, merged rows return to their
 * earlier amounts and rows the move created go. The lines come back first, so a failure half-way
 * leaves an item on both the list and the pantry rather than on neither.
 */
class UndoMoveToPantryUseCase(
    private val shoppingItems: ShoppingItemRepositoryContract,
    private val pantry: PantryRepositoryContract,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        record: PantryMoveRecord,
    ): AppResult<Unit> {
        val lines = shoppingItems.upsertItems(kitchenId, record.listId, record.lines)
        if (lines is AppResult.Failure) return lines
        if (record.before.isNotEmpty()) {
            val restored = pantry.upsertAll(kitchenId, record.before)
            if (restored is AppResult.Failure) return restored
        }
        for (id in record.created) {
            val removed = pantry.remove(kitchenId, id)
            if (removed is AppResult.Failure) return removed
        }
        return AppResult.Success(Unit)
    }
}
