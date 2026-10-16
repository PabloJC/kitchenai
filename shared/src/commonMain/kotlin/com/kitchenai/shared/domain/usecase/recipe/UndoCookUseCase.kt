package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.CookPlan
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.UnitConverter
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase

/**
 * Gives back what a cook took. It adds to the holding as it is now rather than writing the old
 * row back, so a change made to it since the cook (another member's, or a re-stock) is kept; a
 * holding the cook emptied, and so removed, comes back as it was.
 */
class UndoCookUseCase(
    private val pantry: PantryRepositoryContract,
    private val time: TimeProvider,
    private val units: GetUnitConverterUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        cooked: CookPlan,
    ): AppResult<Unit> {
        if (cooked.deductions.isEmpty()) return AppResult.Success(Unit)
        val held = pantry.getPantry(kitchenId)
        if (held is AppResult.Failure) return held
        val converter = units()
        if (converter is AppResult.Failure) return converter
        return when (
            val restored =
                restore(cooked, (held as AppResult.Success).data, (converter as AppResult.Success).data)
        ) {
            is AppResult.Failure -> restored
            is AppResult.Success -> pantry.upsertAll(kitchenId, restored.data)
        }
    }

    private fun restore(
        cooked: CookPlan,
        held: List<PantryItem>,
        converter: UnitConverter,
    ): AppResult<List<PantryItem>> {
        val now = time.now()
        val byId = held.associateBy { it.id }
        val restored = mutableListOf<PantryItem>()
        for (deduction in cooked.deductions) {
            val current = byId[deduction.before.id]
            if (current == null) {
                restored += deduction.before.copy(updatedAt = now)
                continue
            }
            when (val back = current.quantity.plus(deduction.taken, converter)) {
                is AppResult.Failure -> return back
                is AppResult.Success -> restored += current.copy(quantity = back.data, updatedAt = now)
            }
        }
        return AppResult.Success(restored)
    }
}
