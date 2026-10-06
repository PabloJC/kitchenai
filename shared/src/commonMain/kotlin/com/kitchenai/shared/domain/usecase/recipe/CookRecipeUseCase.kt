package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.reaches
import com.kitchenai.shared.domain.model.scaledTo
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher
import com.kitchenai.shared.domain.service.UnitConverter
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItemsUseCase
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase

/**
 * Marks a recipe as cooked: subtracts from the pantry what the recipe used.
 *
 * All or nothing. A recipe that is not fully covered consumes nothing, because a half-applied
 * inventory change is worse than none and this is the closest thing to a transaction the MVP
 * has. Unverifiable lines neither block the cook nor are consumed: nothing here can prove
 * they are held, and nothing can prove they are not.
 */
class CookRecipeUseCase(
    private val recipes: RecipeRepositoryContract,
    private val pantry: PantryRepositoryContract,
    private val consume: ConsumePantryItemsUseCase,
    private val time: TimeProvider,
    private val units: GetUnitConverterUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        recipeId: RecipeId,
        servings: Int,
    ): AppResult<Unit> =
        when (val found = recipes.getRecipe(recipeId)) {
            is AppResult.Failure -> found
            is AppResult.Success -> invoke(kitchenId, found.data, servings)
        }

    /**
     * For a recipe the caller already holds. A generated dish lives nowhere a repository can be
     * asked about, so re-reading it by id would fail for the only kind this app suggests.
     */
    suspend operator fun invoke(
        kitchenId: KitchenId,
        recipe: Recipe,
        servings: Int,
    ): AppResult<Unit> {
        val scaled = recipe.scaledTo(servings)
        if (scaled is AppResult.Failure) return scaled
        val held = pantry.getPantry(kitchenId)
        if (held is AppResult.Failure) return held
        val converter = units()
        if (converter is AppResult.Failure) return converter
        return cook(
            kitchenId,
            (scaled as AppResult.Success).data,
            (held as AppResult.Success).data,
            (converter as AppResult.Success).data,
        )
    }

    private suspend fun cook(
        kitchenId: KitchenId,
        recipe: Recipe,
        held: List<PantryItem>,
        converter: UnitConverter,
    ): AppResult<Unit> {
        val match = PantryMatcher.match(recipe, held, time.now(), converter)
        val missing = match.missing.count { !it.ingredient.optional }
        // The count and not the names: the caller already has the match and renders it itself.
        if (missing > 0) {
            return AppResult.Failure(AppError.Validation("ingredients", "missing required ingredients: $missing"))
        }
        return consume.consume(kitchenId, match.consumptions(held, converter), converter)
    }

    /**
     * Splits each covered line over the holdings that cover it, in the order the matcher found
     * them, each taking what it can give in the recipe's unit. A line with no amount consumes
     * nothing: "some salt" is not a quantity to subtract.
     */
    private fun PantryMatch.consumptions(
        held: List<PantryItem>,
        converter: UnitConverter,
    ): List<Pair<PantryItemId, Quantity>> {
        val byId = held.associateBy { it.id }
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
