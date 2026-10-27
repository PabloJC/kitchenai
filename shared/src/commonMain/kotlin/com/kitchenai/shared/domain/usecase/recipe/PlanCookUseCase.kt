package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.CookPlan
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryDeduction
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.reaches
import com.kitchenai.shared.domain.model.scaledTo
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher
import com.kitchenai.shared.domain.service.UnitConverter
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItemsUseCase
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase

/**
 * What cooking [Recipe] at a number of servings would subtract, holding by holding, without
 * writing anything. Only covered lines are consumed; unverifiable ones neither block nor consume,
 * since nothing here can prove they are held, and nothing can prove they are not.
 */
class PlanCookUseCase(
    private val pantry: PantryRepositoryContract,
    private val consume: ConsumePantryItemsUseCase,
    private val time: TimeProvider,
    private val units: GetUnitConverterUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        recipe: Recipe,
        servings: Int,
    ): AppResult<CookPlan> {
        val scaled = recipe.scaledTo(servings)
        if (scaled is AppResult.Failure) return scaled
        val held = pantry.getPantry(kitchenId)
        if (held is AppResult.Failure) return held
        val converter = units()
        if (converter is AppResult.Failure) return converter
        return plan(
            (scaled as AppResult.Success).data,
            (held as AppResult.Success).data,
            (converter as AppResult.Success).data,
        )
    }

    private fun plan(
        recipe: Recipe,
        held: List<PantryItem>,
        converter: UnitConverter,
    ): AppResult<CookPlan> {
        val match = PantryMatcher.match(recipe, held, time.now(), converter, time.zone())
        val byId = held.associateBy { it.id }
        return when (val touched = consume.apply(held, match.consumptions(byId, converter), converter)) {
            is AppResult.Failure -> touched
            is AppResult.Success ->
                AppResult.Success(
                    CookPlan(
                        deductions = touched.data.map { after -> PantryDeduction(byId.getValue(after.id), after) },
                        missing = match.missing,
                    ),
                )
        }
    }

    /**
     * Splits each covered line over the holdings that cover it, in the order the matcher found
     * them, each taking what it can give in the recipe's unit. A line with no amount consumes
     * nothing: "some salt" is not a quantity to subtract.
     */
    private fun PantryMatch.consumptions(
        byId: Map<PantryItemId, PantryItem>,
        converter: UnitConverter,
    ): List<Pair<PantryItemId, Quantity>> {
        val taken = mutableListOf<Pair<PantryItemId, Quantity>>()
        for (line in covered) {
            val required = line.ingredient.quantity ?: continue
            var left = required.amount
            for (id in line.heldBy) {
                val onHand = byId[id]?.quantity?.let { converter.amountIn(it, required.unit) } ?: 0.0
                val amount = minOf(left, onHand)
                if (amount > 0.0) taken += id to Quantity(amount, required.unit)
                left -= amount
                // Converted amounts can leave a sliver of the line, which is not worth another holding.
                if (0.0.reaches(left)) break
            }
        }
        return taken
    }
}
