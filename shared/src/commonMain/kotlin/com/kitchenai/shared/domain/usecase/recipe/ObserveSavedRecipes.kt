package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import kotlinx.coroutines.flow.Flow

/** Streams the recipes the user has kept. */
class ObserveSavedRecipes(
    private val recipes: RecipeRepositoryContract,
) {
    operator fun invoke(userId: UserId): Flow<List<Recipe>> = recipes.observeSavedRecipes(userId)

    /** The listener's failures, collected alongside the stream above. */
    fun errors(userId: UserId): Flow<AppError> = recipes.savedRecipeErrors(userId)
}
