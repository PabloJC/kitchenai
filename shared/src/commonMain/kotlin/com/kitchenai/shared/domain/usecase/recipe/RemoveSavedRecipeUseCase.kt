package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract

/** Drops a recipe from the kitchen's saved ones. Idempotent: removing twice is not an error. */
class RemoveSavedRecipeUseCase(
    private val recipes: RecipeRepositoryContract,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId,
        recipeId: RecipeId,
    ): AppResult<Unit> = recipes.removeSavedRecipe(kitchenId, recipeId)
}
