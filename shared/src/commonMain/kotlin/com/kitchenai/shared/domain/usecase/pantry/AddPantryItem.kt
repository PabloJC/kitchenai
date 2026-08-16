package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import kotlin.time.Instant

/**
 * Adds a holding to the pantry.
 *
 * Merging is the whole reason this is a use case and not a port call: buying more of
 * something already held in the same unit tops up that row instead of leaving two rows the
 * user has to reconcile. Different units never merge — the MVP converts nothing.
 */
class AddPantryItem(
    private val pantry: PantryRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        ingredient: IngredientId,
        quantity: Quantity,
        location: TermRef?,
        expiresAt: Instant?,
    ): AppResult<PantryItem> =
        if (quantity.amount <= 0.0) {
            AppResult.Failure(AppError.Validation("amount", "must be greater than zero"))
        } else {
            when (val held = pantry.getPantry(userId)) {
                is AppResult.Failure -> held
                is AppResult.Success -> write(userId, held.data, ingredient, quantity, location, expiresAt)
            }
        }

    private suspend fun write(
        userId: UserId,
        held: List<PantryItem>,
        ingredient: IngredientId,
        quantity: Quantity,
        location: TermRef?,
        expiresAt: Instant?,
    ): AppResult<PantryItem> {
        val now = time.now()
        val mergeInto = held.firstOrNull { it.ingredient == ingredient && it.quantity.canCombineWith(quantity) }
        val built =
            mergeInto?.let { existing ->
                (existing.quantity + quantity).map { total ->
                    existing.copy(
                        quantity = total,
                        location = location ?: existing.location,
                        // A merged pile is only as good as its soonest expiry.
                        expiresAt = listOfNotNull(existing.expiresAt, expiresAt).minOrNull(),
                        updatedAt = now,
                    )
                }
            } ?: PantryItemId.of(ids.newId()).map { id ->
                PantryItem(id, ingredient, quantity, location, expiresAt, now)
            }
        return when (built) {
            is AppResult.Failure -> built
            is AppResult.Success -> pantry.upsert(userId, built.data).map { built.data }
        }
    }
}
