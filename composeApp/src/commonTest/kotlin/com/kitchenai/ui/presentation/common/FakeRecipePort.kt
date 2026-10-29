package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A catalogue that can also refuse, because a fake that only succeeds tests only success.
 * [stored] seeds the local generation cache as one generation.
 */
class FakeRecipePort(
    private val catalogue: List<Recipe> = emptyList(),
    stored: List<Recipe> = emptyList(),
    private val readError: AppError? = null,
) : RecipeRepositoryContract {
    private val state = MutableStateFlow<List<Recipe>>(emptyList())

    // Newest first, like the real cache.
    private val generations = MutableStateFlow(listOf(stored).filter { it.isNotEmpty() })

    val saved: List<Recipe> get() = state.value

    override fun observeSavedRecipes(kitchenId: KitchenId): Flow<List<Recipe>> =
        if (readError == null) state else emptyFlow()

    override fun savedRecipeErrors(kitchenId: KitchenId): Flow<AppError> = readError?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe> {
        readError?.let { return AppResult.Failure(it) }
        val found = (state.value + catalogue).firstOrNull { it.id == recipeId }
        return found?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound("recipe"))
    }

    override suspend fun saveRecipe(
        kitchenId: KitchenId,
        recipe: Recipe,
    ): AppResult<Unit> {
        state.value = state.value.filterNot { it.id == recipe.id } + recipe
        return AppResult.Success(Unit)
    }

    override suspend fun removeSavedRecipe(
        kitchenId: KitchenId,
        recipeId: RecipeId,
    ): AppResult<Unit> {
        state.value = state.value.filterNot { it.id == recipeId }
        return AppResult.Success(Unit)
    }

    override suspend fun getAll(): AppResult<List<Recipe>> = AppResult.Success(generations.value.flatten())

    override suspend fun getGenerations(): AppResult<List<List<Recipe>>> = AppResult.Success(generations.value)

    /** A recipe stored again moves to the newest generation, as a row replaced by id does in the real table. */
    override suspend fun addGeneration(
        recipes: List<Recipe>,
        keep: Int,
    ): AppResult<Unit> {
        val ids = recipes.map { it.id }.toSet()
        val older =
            generations.value.map {
                    generation ->
                generation.filterNot { it.id in ids }
            }.filter { it.isNotEmpty() }
        generations.value = (listOf(recipes) + older).take(keep)
        return AppResult.Success(Unit)
    }
}
