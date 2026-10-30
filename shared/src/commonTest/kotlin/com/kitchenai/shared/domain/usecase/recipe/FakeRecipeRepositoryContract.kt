package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
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
 * can read one by id without first putting it in the kitchen's library. [stored] seeds the local
 * generation cache as one generation, a separate list from [catalogue] and [saved].
 */
class FakeRecipeRepositoryContract(
    saved: List<Recipe> = emptyList(),
    private val catalogue: List<Recipe> = emptyList(),
    stored: List<Recipe> = emptyList(),
    private val readError: AppError? = null,
    private val writeError: AppError? = null,
    /** What `attachImage` answers with: an address, or the failure the image call had. */
    private val imageResult: AppResult<String> = AppResult.Success("https://images.test/recipe.jpg"),
) : RecipeRepositoryContract {
    /** Every recipe `attachImage` was asked about, so a test can tell "no call" from a call. */
    val imageRequests = mutableListOf<Recipe>()

    private val state = MutableStateFlow(saved)

    // Newest first, like the real cache.
    private val generations = MutableStateFlow(listOf(stored).filter { it.isNotEmpty() })

    val recipes: List<Recipe> get() = state.value

    // A failing listener stops emitting and reports on its keyed error stream, which is what
    // the real adapter does with a Firestore snapshot error.
    override fun observeSavedRecipes(kitchenId: KitchenId): Flow<List<Recipe>> =
        if (readError == null) state else emptyFlow()

    override fun savedRecipeErrors(kitchenId: KitchenId): Flow<AppError> = readError?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe> {
        readError?.let { return AppResult.Failure(it) }
        val found = (state.value + catalogue).firstOrNull { it.id == recipeId }
        return found?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound("Recipe"))
    }

    override suspend fun saveRecipe(
        kitchenId: KitchenId,
        recipe: Recipe,
    ): AppResult<Unit> = write { saved -> saved.filterNot { it.id == recipe.id } + recipe }

    override suspend fun attachImage(
        kitchenId: KitchenId?,
        recipe: Recipe,
    ): AppResult<Recipe> {
        imageRequests += recipe
        return when (imageResult) {
            is AppResult.Failure -> imageResult
            is AppResult.Success -> AppResult.Success(recipe.copy(imageUrl = imageResult.data))
        }
    }

    override suspend fun removeSavedRecipe(
        kitchenId: KitchenId,
        recipeId: RecipeId,
    ): AppResult<Unit> = write { saved -> saved.filterNot { it.id == recipeId } }

    override suspend fun getAll(): AppResult<List<Recipe>> =
        readError?.let { AppResult.Failure(it) } ?: AppResult.Success(generations.value.flatten())

    override suspend fun getGenerations(): AppResult<List<List<Recipe>>> =
        readError?.let { AppResult.Failure(it) } ?: AppResult.Success(generations.value)

    override suspend fun addGeneration(
        recipes: List<Recipe>,
        keep: Int,
    ): AppResult<Unit> =
        writeError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit).also {
            generations.value = withGeneration(generations.value, recipes, keep)
        }

    private fun write(edit: (List<Recipe>) -> List<Recipe>): AppResult<Unit> =
        writeError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit).also { state.value = edit(state.value) }
}

// Fixtures. Titles and free text are placeholders derived from an id: naming a dish, a cuisine
// or an ingredient in a fixture is the same mistake as naming it in code.
internal val kitchen: KitchenId = (KitchenId.of("kitchen-1") as AppResult.Success).data

// The profile owner, kept separate from [kitchen]: #191 scopes saved recipes and the pantry to
// the kitchen, but a profile — read by SuggestRecipesUseCase — is still read by user.
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

/** A recipe stored again moves to the newest generation, as a row replaced by id does in the real table. */
internal fun withGeneration(
    kept: List<List<Recipe>>,
    recipes: List<Recipe>,
    keep: Int,
): List<List<Recipe>> {
    val ids = recipes.map { it.id }.toSet()
    val older = kept.map { generation -> generation.filterNot { it.id in ids } }.filter { it.isNotEmpty() }
    return (listOf(recipes) + older).take(keep)
}
