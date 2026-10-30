package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.port.RecipeRepositoryContract

/**
 * Gives [Recipe] its own photograph, once. A recipe that already has one, or that the server
 * issued no key for (catalogue dishes, older suggestions), comes back untouched without a call.
 */
class GenerateRecipeImageUseCase(
    private val recipes: RecipeRepositoryContract,
) {
    suspend operator fun invoke(
        kitchenId: KitchenId?,
        recipe: Recipe,
    ): AppResult<Recipe> =
        if (recipe.imageUrl != null || recipe.imageKey == null) {
            AppResult.Success(recipe)
        } else {
            recipes.attachImage(kitchenId, recipe)
        }
}
