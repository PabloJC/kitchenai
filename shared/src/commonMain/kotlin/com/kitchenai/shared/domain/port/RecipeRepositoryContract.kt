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

    /** Every recipe in the local generation cache, from every generation still kept. */
    suspend fun getAll(): AppResult<List<Recipe>>

    /** The kept generations, newest first, each in the order it was generated. */
    suspend fun getGenerations(): AppResult<List<List<Recipe>>>

    /** Stores [recipes] as the newest generation and drops all but the newest [keep]. */
    suspend fun addGeneration(
        recipes: List<Recipe>,
        keep: Int,
    ): AppResult<Unit>
}
