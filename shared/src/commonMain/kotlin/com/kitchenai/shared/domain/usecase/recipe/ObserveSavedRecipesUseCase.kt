package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import kotlinx.coroutines.flow.Flow

/** Streams the recipes the kitchen has kept. */
class ObserveSavedRecipesUseCase(
    private val recipes: RecipeRepositoryContract,
) {
    operator fun invoke(kitchenId: KitchenId): Flow<List<Recipe>> = recipes.observeSavedRecipes(kitchenId)

    /** The listener's failures, collected alongside the stream above. */
    fun errors(kitchenId: KitchenId): Flow<AppError> = recipes.savedRecipeErrors(kitchenId)
}
