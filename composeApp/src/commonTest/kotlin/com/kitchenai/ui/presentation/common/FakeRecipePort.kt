package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.RecipePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** A catalogue that can also refuse, because a fake that only succeeds tests only success. */
class FakeRecipePort(
    private val catalogue: List<Recipe> = emptyList(),
    private val readError: AppError? = null,
) : RecipePort {
    private val state = MutableStateFlow<List<Recipe>>(emptyList())

    val saved: List<Recipe> get() = state.value

    override fun observeSavedRecipes(userId: UserId): Flow<List<Recipe>> = if (readError == null) state else emptyFlow()

    override fun savedRecipeErrors(userId: UserId): Flow<AppError> = readError?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe> {
        readError?.let { return AppResult.Failure(it) }
        val found = (state.value + catalogue).firstOrNull { it.id == recipeId }
        return found?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound("recipe"))
    }

    override suspend fun saveRecipe(
        userId: UserId,
        recipe: Recipe,
    ): AppResult<Unit> {
        state.value = state.value.filterNot { it.id == recipe.id } + recipe
        return AppResult.Success(Unit)
    }

    override suspend fun removeSavedRecipe(
        userId: UserId,
        recipeId: RecipeId,
    ): AppResult<Unit> {
        state.value = state.value.filterNot { it.id == recipeId }
        return AppResult.Success(Unit)
    }
}
