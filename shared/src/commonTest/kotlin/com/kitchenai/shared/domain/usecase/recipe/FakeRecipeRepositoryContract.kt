package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [RecipeRepositoryContract]. [catalogue] holds recipes nobody saved, so that a test
 * can read one by id without first putting it in the user's library. [stored] seeds the local
 * generation cache, a separate list from [catalogue] and [saved].
 */
class FakeRecipeRepositoryContract(
    saved: List<Recipe> = emptyList(),
    private val catalogue: List<Recipe> = emptyList(),
    stored: List<Recipe> = emptyList(),
    private val readError: AppError? = null,
    private val writeError: AppError? = null,
) : RecipeRepositoryContract {
    private val state = MutableStateFlow(saved)
    private val cache = MutableStateFlow(stored)

    val recipes: List<Recipe> get() = state.value

    // A failing listener stops emitting and reports on its keyed error stream, which is what
    // the real adapter does with a Firestore snapshot error.
    override fun observeSavedRecipes(userId: UserId): Flow<List<Recipe>> = if (readError == null) state else emptyFlow()

    override fun savedRecipeErrors(userId: UserId): Flow<AppError> = readError?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe> {
        readError?.let { return AppResult.Failure(it) }
        val found = (state.value + catalogue).firstOrNull { it.id == recipeId }
        return found?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound("Recipe"))
    }

    override suspend fun saveRecipe(
        userId: UserId,
        recipe: Recipe,
    ): AppResult<Unit> = write { saved -> saved.filterNot { it.id == recipe.id } + recipe }

    override suspend fun removeSavedRecipe(
        userId: UserId,
        recipeId: RecipeId,
    ): AppResult<Unit> = write { saved -> saved.filterNot { it.id == recipeId } }

    override suspend fun getAll(): AppResult<List<Recipe>> =
        readError?.let { AppResult.Failure(it) } ?: AppResult.Success(cache.value)

    override suspend fun replaceAll(recipes: List<Recipe>): AppResult<Unit> =
        writeError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit).also { cache.value = recipes }

    private fun write(edit: (List<Recipe>) -> List<Recipe>): AppResult<Unit> =
        writeError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit).also { state.value = edit(state.value) }
}

// Fixtures. Titles and free text are placeholders derived from an id: naming a dish, a cuisine
// or an ingredient in a fixture is the same mistake as naming it in code.
internal val user: UserId = (UserId.of("user-1") as AppResult.Success).data

internal fun recipeId(raw: String): RecipeId = (RecipeId.of(raw) as AppResult.Success).data

internal fun ingredientId(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

internal fun recipeIngredient(
    ingredient: String? = null,
    freeText: String? = null,
    quantity: Quantity? = null,
    optional: Boolean = false,
): RecipeIngredient =
    (
        RecipeIngredient.create(
            ingredient = ingredient?.let(::ingredientId),
            freeText = freeText,
            quantity = quantity,
            optional = optional,
        ) as AppResult.Success
    ).data

internal fun recipe(
    id: String = "recipe-1",
    servings: Int = 2,
    ingredients: List<RecipeIngredient> = emptyList(),
): Recipe =
    Recipe(
        id = recipeId(id),
        title = "title-$id",
        summary = null,
        servings = servings,
        totalMinutes = null,
        ingredients = ingredients,
        steps = emptyList(),
        tags = emptyList(),
        source = RecipeSource.Catalogue,
    )
