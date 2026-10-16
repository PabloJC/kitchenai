package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.CookPlan
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItemsUseCase

/**
 * Marks a recipe as cooked: subtracts from the pantry what [PlanCookUseCase] says it used, and
 * returns that plan so the cook can be undone.
 *
 * A missing required line refuses the cook unless [invoke]'s `anyway` is set, in which case
 * only what is held is subtracted. The plan is computed in full before anything is written.
 */
class CookRecipeUseCase(
    private val recipes: RecipeRepositoryContract,
    private val plan: PlanCookUseCase,
    private val consume: ConsumePantryItemsUseCase,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        recipeId: RecipeId,
        servings: Int,
        anyway: Boolean = false,
    ): AppResult<CookPlan> =
        when (val found = recipes.getRecipe(recipeId)) {
            is AppResult.Failure -> found
            is AppResult.Success -> invoke(kitchenId, found.data, servings, anyway)
        }

    /**
     * For a recipe the caller already holds. A generated dish lives nowhere a repository can be
     * asked about, so re-reading it by id would fail for the only kind this app suggests.
     */
    suspend operator fun invoke(
        kitchenId: KitchenId,
        recipe: Recipe,
        servings: Int,
        anyway: Boolean = false,
    ): AppResult<CookPlan> {
        val planned =
            when (val result = plan(kitchenId, recipe, servings)) {
                is AppResult.Failure -> return result
                is AppResult.Success -> result.data
            }
        if (!planned.isComplete && !anyway) {
            val missing = planned.missing.count { !it.ingredient.optional }
            // The count and not the names: the caller already has the match and renders it itself.
            return AppResult.Failure(AppError.Validation("ingredients", "missing required ingredients: $missing"))
        }
        if (planned.deductions.isEmpty()) return AppResult.Success(planned)
        return when (val written = consume.write(kitchenId, planned.deductions.map { it.after })) {
            is AppResult.Failure -> written
            is AppResult.Success -> AppResult.Success(planned)
        }
    }
}
