package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.port.RecipeRepositoryContract

/** Replaces the stored generation with a new one, wholesale. */
class StoreSuggestions(
    private val recipes: RecipeRepositoryContract,
) {
    suspend operator fun invoke(generated: List<Recipe>): AppResult<Unit> = recipes.replaceAll(generated)
}
