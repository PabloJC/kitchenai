package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.port.RecipePort

/** Reads one recipe, catalogue or saved. */
class GetRecipeById(
    private val recipes: RecipePort,
) {
    suspend operator fun invoke(recipeId: RecipeId): AppResult<Recipe> = recipes.getRecipe(recipeId)
}
