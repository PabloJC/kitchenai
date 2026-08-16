package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.scaledTo
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.RecipePort
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItems

/**
 * Marks a recipe as cooked: subtracts from the pantry what the recipe used.
 *
 * All or nothing. A recipe that is not fully covered consumes nothing, because a half-applied
 * inventory change is worse than none and this is the closest thing to a transaction the MVP
 * has. Unverifiable lines neither block the cook nor are consumed: nothing here can prove
 * they are held, and nothing can prove they are not.
 */
class CookRecipe(
    private val recipes: RecipePort,
    private val pantry: PantryRepositoryContract,
    private val consume: ConsumePantryItems,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(
        userId: UserId,
        recipeId: RecipeId,
        servings: Int,
    ): AppResult<Unit> =
        when (val found = recipes.getRecipe(recipeId)) {
            is AppResult.Failure -> found
            is AppResult.Success -> invoke(userId, found.data, servings)
        }

    /**
     * For a recipe the caller already holds. A generated dish lives nowhere a repository can be
     * asked about, so re-reading it by id would fail for the only kind this app suggests.
     */
    suspend operator fun invoke(
        userId: UserId,
        recipe: Recipe,
        servings: Int,
    ): AppResult<Unit> {
        val scaled = recipe.scaledTo(servings)
        if (scaled is AppResult.Failure) return scaled
        val held = pantry.getPantry(userId)
        if (held is AppResult.Failure) return held
        return cook(userId, (scaled as AppResult.Success).data, (held as AppResult.Success).data)
    }

    private suspend fun cook(
        userId: UserId,
        recipe: Recipe,
        held: List<PantryItem>,
    ): AppResult<Unit> {
        val match = PantryMatcher.match(recipe, held, time.now())
        val missing = match.missing.count { !it.ingredient.optional }
        // The count and not the names: the caller already has the match and renders it itself.
        if (missing > 0) {
            return AppResult.Failure(AppError.Validation("ingredients", "missing required ingredients: $missing"))
        }
        return consume(userId, match.consumptions(held))
    }

    /**
     * Splits each covered line over the holdings that cover it, in the order the matcher found
     * them. A line with no amount consumes nothing: "some salt" is not a quantity to subtract.
     */
    private fun PantryMatch.consumptions(held: List<PantryItem>): List<Pair<PantryItemId, Quantity>> {
        val byId = held.associateBy { it.id }
        val taken = mutableListOf<Pair<PantryItemId, Quantity>>()
        for (line in covered) {
            val required = line.ingredient.quantity ?: continue
            var left = required.amount
            for (id in line.heldBy) {
                // The matcher only reports holdings in the required unit, so nothing converts.
                val amount = minOf(left, byId[id]?.quantity?.amount ?: 0.0)
                if (amount > 0.0) taken += id to Quantity(amount, required.unit)
                left -= amount
                if (left <= 0.0) break
            }
        }
        return taken
    }
}
