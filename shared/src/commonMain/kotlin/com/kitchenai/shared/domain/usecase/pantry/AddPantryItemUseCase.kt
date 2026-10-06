package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import kotlin.time.Instant

/**
 * Adds a holding to the pantry.
 *
 * Merging is the whole reason this is a use case and not a port call: buying more of
 * something already held in the same unit, or one that converts into it, tops up that row
 * instead of leaving two rows the user has to reconcile. Units that do not convert never merge.
 */
class AddPantryItemUseCase(
    private val pantry: PantryRepositoryContract,
    private val ids: IdGenerator,
    private val time: TimeProvider,
    private val units: GetUnitConverterUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
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
                when (val held = pantry.getPantry(kitchenId)) {
                    is AppResult.Failure -> held
                    is AppResult.Success ->
                        write(kitchenId, held.data, ingredient, text, quantity, location, expiresAt)
                }
        }
    }

    private suspend fun write(
        kitchenId: KitchenId,
        held: List<PantryItem>,
        ingredient: IngredientId?,
        freeText: String?,
        quantity: Quantity,
        location: TermRef?,
        expiresAt: Instant?,
    ): AppResult<PantryItem> {
        val converter =
            when (val loaded = units()) {
                is AppResult.Failure -> return loaded
                is AppResult.Success -> loaded.data
            }
        val built =
            draftPantryHolding(held, ingredient, freeText, quantity, location, expiresAt, ids, time.now(), converter)
        return when (built) {
            is AppResult.Failure -> built
            is AppResult.Success -> pantry.upsert(kitchenId, built.data).map { built.data }
        }
    }
}
