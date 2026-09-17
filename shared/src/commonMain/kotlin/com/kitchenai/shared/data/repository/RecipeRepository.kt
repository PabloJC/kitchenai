package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.local.RecipeLocalDataSource
import com.kitchenai.shared.data.local.localCall
import com.kitchenai.shared.data.local.toEntity
import com.kitchenai.shared.data.local.toRecipe
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.data.remote.firebase.RecipeDocument
import com.kitchenai.shared.data.remote.firebase.RecipeRemoteDataSource
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [RecipeRepositoryContract] over both recipe data sources: [RecipeRemoteDataSource] answers the
 * catalogue and saved recipes, [RecipeLocalDataSource] the local generation cache. Each method
 * reaches exactly one of them — #139 moved the boundary between "local" and "remote" into one
 * contract, it did not blend what either one answers.
 *
 * Reuses the [Recipe] to [RecipeDto] conversion for both: the shape a recipe travels in does not
 * depend on where it is going.
 */
class RecipeRepository(
    private val localDataSource: RecipeLocalDataSource,
    private val remoteDataSource: RecipeRemoteDataSource,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : RecipeRepositoryContract {
    // --- Saved recipes and the catalogue, over RecipeRemoteDataSource ---

    // Still keyed by the uid RecipeRemoteDataSource was built for, via KitchenId.asUserId —
    // #192 repoints it at kitchens/{kitchenId}/..., at which point this bridging disappears.
    override fun observeSavedRecipes(kitchenId: KitchenId): Flow<List<Recipe>> =
        remoteDataSource
            .observeSavedRecipes(kitchenId.asUserId())
            .map { documents -> documents.map { it.dto.toDomain(it.id) }.decodedOrDropped() }

    override fun savedRecipeErrors(kitchenId: KitchenId): Flow<AppError> =
        remoteDataSource.savedRecipeErrors(kitchenId.asUserId())

    override suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe> =
        firstFound(RECIPE_RESOURCE, recipeReads(recipeId)).flatMap { document -> document.dto.toDomain(document.id) }

    override suspend fun saveRecipe(
        kitchenId: KitchenId,
        recipe: Recipe,
    ): AppResult<Unit> = remoteDataSource.save(kitchenId.asUserId(), recipe.id, recipe.toDto(time.now()))

    override suspend fun removeSavedRecipe(
        kitchenId: KitchenId,
        recipeId: RecipeId,
    ): AppResult<Unit> = remoteDataSource.remove(kitchenId.asUserId(), recipeId)

    /** A saved copy is looked up before the catalogue, since a generated recipe exists nowhere else. */
    private fun recipeReads(recipeId: RecipeId): List<suspend () -> AppResult<RecipeDocument?>> =
        buildList {
            remoteDataSource.currentUserId()?.let { userId ->
                add { remoteDataSource.getSavedRecipe(userId, recipeId) }
            }
            add { remoteDataSource.getCataloguedRecipe(recipeId) }
        }

    // --- The local generation cache, over RecipeLocalDataSource ---

    override suspend fun getAll(): AppResult<List<Recipe>> =
        localCall(dispatchers) { localDataSource.getAll() }
            .map { entities -> entities.map { it.toRecipe() }.decodedOrDropped() }

    override suspend fun replaceAll(recipes: List<Recipe>): AppResult<Unit> =
        localCall(dispatchers) { localDataSource.replaceAll(recipes.map { it.toEntity(time.now()) }) }

    private companion object {
        // The collection, never the identifier: an error carries no user content.
        const val RECIPE_RESOURCE = "recipe"
    }
}
