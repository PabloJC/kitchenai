package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract

/** Keeps a recipe under the user, whatever produced it. */
class SaveRecipeUseCase(
    private val recipes: RecipeRepositoryContract,
) {
    suspend operator fun invoke(
        userId: UserId,
        recipe: Recipe,
    ): AppResult<Unit> =
        // A generated recipe with no ingredients is a failed generation: saving it would put a
        // card in the library that can never be matched or shopped for.
        if (recipe.ingredients.isEmpty()) {
            AppResult.Failure(AppError.Validation("ingredients", "must not be empty"))
        } else {
            recipes.saveRecipe(userId, recipe)
        }
}
