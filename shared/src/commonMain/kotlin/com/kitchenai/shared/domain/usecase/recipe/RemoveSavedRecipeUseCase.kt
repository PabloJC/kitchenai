package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract

/** Drops a recipe from the user's saved ones. Idempotent: removing twice is not an error. */
class RemoveSavedRecipeUseCase(
    private val recipes: RecipeRepositoryContract,
) {
    suspend operator fun invoke(
        userId: UserId,
        recipeId: RecipeId,
    ): AppResult<Unit> = recipes.removeSavedRecipe(userId, recipeId)
}
