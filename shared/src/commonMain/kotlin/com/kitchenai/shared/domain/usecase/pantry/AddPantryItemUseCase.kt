package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
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
class AddPantryItemUseCase(
    private val pantry: PantryRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        ingredient: IngredientId?,
        freeText: String?,
        quantity: Quantity,
        location: TermRef?,
        expiresAt: Instant?,
    ): AppResult<PantryItem> {
        // Checked here rather than left to PantryItem.create(): the merge branch below never
        // calls create() (it copies an existing, already-valid holding), so a caller passing
        // both would otherwise have its freeText silently dropped instead of rejected.
        val text = freeText?.takeIf { it.isNotBlank() }
        return when {
            (ingredient == null) == (text == null) ->
                AppResult.Failure(
                    AppError.Validation("ingredient", "exactly one of ingredient or freeText must be set"),
                )
            quantity.amount <= 0.0 -> AppResult.Failure(AppError.Validation("amount", "must be greater than zero"))
            else ->
                when (val held = pantry.getPantry(userId)) {
                    is AppResult.Failure -> held
                    is AppResult.Success -> write(userId, held.data, ingredient, text, quantity, location, expiresAt)
                }
        }
    }

    private suspend fun write(
        userId: UserId,
        held: List<PantryItem>,
        ingredient: IngredientId?,
        freeText: String?,
        quantity: Quantity,
        location: TermRef?,
        expiresAt: Instant?,
    ): AppResult<PantryItem> {
        val built = draftPantryHolding(held, ingredient, freeText, quantity, location, expiresAt, ids, time.now())
        return when (built) {
            is AppResult.Failure -> built
            is AppResult.Success -> pantry.upsert(userId, built.data).map { built.data }
        }
    }
}
