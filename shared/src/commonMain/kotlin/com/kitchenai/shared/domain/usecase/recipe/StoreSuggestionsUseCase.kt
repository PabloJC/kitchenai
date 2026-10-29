package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.port.RecipeRepositoryContract

/**
 * Stores a new generation beside the ones before it, keeping the last [KEPT_GENERATIONS]: New
 * ideas must not cost the dishes someone was still looking at.
 */
class StoreSuggestionsUseCase(
    private val recipes: RecipeRepositoryContract,
) {
    suspend operator fun invoke(generated: List<Recipe>): AppResult<Unit> =
        if (generated.isEmpty()) AppResult.Success(Unit) else recipes.addGeneration(generated, KEPT_GENERATIONS)

    companion object {
        /** The newest plus two earlier ones: enough to go back, few enough that the device keeps no archive. */
        const val KEPT_GENERATIONS = 3
    }
}
