package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract

/** One recipe from the last generation, by id — a generated dish was never written anywhere else. */
class GetStoredRecipe(
    private val recipes: RecipeRepositoryContract,
) {
    suspend operator fun invoke(recipeId: RecipeId): AppResult<Recipe?> =
        recipes.getAll().map { stored -> stored.firstOrNull { it.id == recipeId } }
}
