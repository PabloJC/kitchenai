package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import kotlinx.coroutines.flow.Flow

/**
 * The recipe seam: the read-only catalogue, a kitchen's saved recipes, and the local generation
 * cache #137 added — one contract for "how this app reads and writes a `Recipe`", backed by a
 * remote data source for the first two and a local one for the third (#139).
 *
 * [getRecipe] takes no [KitchenId] because a recipe is readable from the shared catalogue as well
 * as from a saved copy; everything that belongs to one kitchen is keyed by it.
 */
interface RecipeRepositoryContract {
    fun observeSavedRecipes(kitchenId: KitchenId): Flow<List<Recipe>>

    /** Failures of the listener above, keyed like it. */
    fun savedRecipeErrors(kitchenId: KitchenId): Flow<AppError>

    /**
     * One-shot read for the use cases that need a recipe now: taking the first emission of the
     * listener would hang forever once that listener has failed.
     */
    suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe>

    suspend fun saveRecipe(
        kitchenId: KitchenId,
        recipe: Recipe,
    ): AppResult<Unit>

    suspend fun removeSavedRecipe(
        kitchenId: KitchenId,
        recipeId: RecipeId,
    ): AppResult<Unit>

    /** The whole local generation cache. */
    suspend fun getAll(): AppResult<List<Recipe>>

    /**
     * Replaces the whole local generation cache. Its first caller is a generation that
     * supersedes the one before it wholesale, not a row that changes on its own — that is also
     * why there is no single-recipe write here yet.
     */
    suspend fun replaceAll(recipes: List<Recipe>): AppResult<Unit>
}
