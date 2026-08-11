package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryPort
import com.kitchenai.shared.domain.port.TimeProvider

/**
 * Subtracts what has been used from the pantry.
 *
 * Everything is computed before anything is written, so a consumption that does not fit
 * leaves the pantry exactly as it was rather than half applied.
 */
class ConsumePantryItems(
    private val pantry: PantryPort,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        consumptions: List<Pair<PantryItemId, Quantity>>,
    ): AppResult<Unit> =
        when (val held = pantry.getPantry(userId)) {
            is AppResult.Failure -> held
            is AppResult.Success ->
                when (val applied = apply(held.data, consumptions)) {
                    is AppResult.Failure -> applied
                    is AppResult.Success -> write(userId, applied.data)
                }
        }

    /**
     * Returns only the touched holdings, so an unrelated row is never rewritten.
     *
     * Five exits, one per rejection reason: collapsing them would hide which check failed.
     */
    @Suppress("ReturnCount")
    private fun apply(
        held: List<PantryItem>,
        consumptions: List<Pair<PantryItemId, Quantity>>,
    ): AppResult<List<PantryItem>> {
        val now = time.now()
        val byId = held.associateBy { it.id }
        val touched = LinkedHashMap<PantryItemId, PantryItem>()
        for ((id, taken) in consumptions) {
            // Without this guard a negative amount would add to the holding through the
            // subtraction, bypassing the merge AddPantryItem enforces.
            if (taken.amount <= 0.0) {
                return AppResult.Failure(AppError.Validation("amount", "must be greater than zero"))
            }
            val item = touched[id] ?: byId[id] ?: return AppResult.Failure(AppError.NotFound("PantryItem"))
            val left =
                when (val rest = item.quantity - taken) {
                    is AppResult.Failure -> return rest
                    is AppResult.Success -> rest.data
                }
            if (left.amount < 0.0) {
                return AppResult.Failure(AppError.Validation("amount", "consumption exceeds the quantity held"))
            }
            touched[id] = item.copy(quantity = left, updatedAt = now)
        }
        return AppResult.Success(touched.values.toList())
    }

    private suspend fun write(
        userId: UserId,
        touched: List<PantryItem>,
    ): AppResult<Unit> {
        val (depleted, survivors) = touched.partition { it.quantity.amount <= 0.0 }
        // Survivors leave in a single batched write; the port has no batched delete for the
        // rows that reached zero.
        var result: AppResult<Unit> =
            if (survivors.isEmpty()) AppResult.Success(Unit) else pantry.upsertAll(userId, survivors)
        for (item in depleted) {
            if (result is AppResult.Failure) return result
            result = pantry.remove(userId, item.id)
        }
        return result
    }
}
