package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/**
 * The recipe seam: the user's saved recipes, plus one-shot reads of a single recipe.
 *
 * [getRecipe] takes no [UserId] because a recipe is readable from the shared catalogue as well
 * as from a saved copy; everything that belongs to one person is keyed by the user.
 */
interface RecipePort {
    fun observeSavedRecipes(userId: UserId): Flow<List<Recipe>>

    /** Failures of the listener above, keyed like it. */
    fun savedRecipeErrors(userId: UserId): Flow<AppError>

    /**
     * One-shot read for the use cases that need a recipe now: taking the first emission of the
     * listener would hang forever once that listener has failed.
     */
    suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe>

    suspend fun saveRecipe(
        userId: UserId,
        recipe: Recipe,
    ): AppResult<Unit>

    suspend fun removeSavedRecipe(
        userId: UserId,
        recipeId: RecipeId,
    ): AppResult<Unit>
}
