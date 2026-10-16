package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItemsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.shared.domain.usecase.recipe.CookRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetRecipeByIdUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetStoredRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.MatchRecipeAgainstPantryUseCase
import com.kitchenai.shared.domain.usecase.recipe.ObserveSavedRecipesUseCase
import com.kitchenai.shared.domain.usecase.recipe.PlanCookUseCase
import com.kitchenai.shared.domain.usecase.recipe.RemoveSavedRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.SaveRecipeUseCase
import com.kitchenai.shared.domain.usecase.recipe.UndoCookUseCase
import com.kitchenai.shared.domain.usecase.shopping.AddMissingIngredientsToShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeRecipePort
import com.kitchenai.ui.presentation.common.FakeShoppingItemPort
import com.kitchenai.ui.presentation.common.FakeShoppingListPort
import com.kitchenai.ui.presentation.common.FakeTaxonomyPort
import com.kitchenai.ui.presentation.common.noUnits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.time.Instant

/** The detail screen's real use cases over fake ports, shared by its ViewModel test classes. */
@OptIn(ExperimentalCoroutinesApi::class)
open class RecipeDetailViewModelHarness {
    protected val dispatcher = StandardTestDispatcher()
    protected val now = Instant.fromEpochSeconds(1_000)
    protected val user = UserId.of("user-1").orFail()
    protected val gram = TermRef(TaxonomyId.of("units").orFail(), TermId.of("gram").orFail())

    /** One catalogue line the pantry can check, and one free-text line it never can. */
    protected val dish =
        Recipe(
            id = RecipeId.of("recipe-1").orFail(),
            title = "Dish",
            summary = "A dish",
            servings = 2,
            totalMinutes = 20,
            ingredients =
                listOf(
                    RecipeIngredient(IngredientId.of("rice").orFail(), null, Quantity(200.0, gram), false),
                    RecipeIngredient(null, "a pinch of salt", null, false),
                ),
            steps = listOf("Cook it"),
            tags = emptyList(),
            source = RecipeSource.Catalogue,
        )

    protected fun holding(amount: Double): PantryItem =
        PantryItem(
            id = PantryItemId.of("item-1").orFail(),
            ingredient = IngredientId.of("rice").orFail(),
            freeText = null,
            quantity = Quantity(amount, gram),
            location = null,
            expiresAt = null,
            updatedAt = now,
        )

    protected fun freeTextHolding(
        id: String,
        freeText: String,
    ): PantryItem =
        PantryItem(
            id = PantryItemId.of(id).orFail(),
            ingredient = null,
            freeText = freeText,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = null,
            updatedAt = now,
        )

    protected val taxonomies = FakeTaxonomyPort()
    protected val catalogue = FakeIngredientPort()
    protected val items = FakeShoppingItemPort()
    protected var lists = FakeShoppingListPort()
    protected val kitchens = FakeKitchenPort()

    protected fun started(
        pantry: List<PantryItem>,
        pantryPort: PantryRepositoryContract = FakePantryPort(pantry),
        recipePort: RecipeRepositoryContract = FakeRecipePort(catalogue = listOf(dish)),
        recipeId: RecipeId = dish.id,
    ): RecipeDetailViewModel {
        val time = TimeProvider { now }
        val consume = ConsumePantryItemsUseCase(pantryPort, time, noUnits())
        val planCook = PlanCookUseCase(pantryPort, consume, time, noUnits())
        val reads =
            RecipeDetailReadsDelegate(
                recipe = GetRecipeByIdUseCase(recipePort),
                storedRecipe = GetStoredRecipeUseCase(recipePort),
                match = MatchRecipeAgainstPantryUseCase(recipePort, pantryPort, time, noUnits()),
                ingredients = ObserveIngredientsUseCase(catalogue),
                taxonomies = ObserveTaxonomiesUseCase(taxonomies),
                taxonomy = ObserveTaxonomyUseCase(taxonomies),
                pantry = ObservePantryUseCase(pantryPort),
                saved = ObserveSavedRecipesUseCase(recipePort),
            )
        val writes =
            RecipeDetailWritesDelegate(
                save = SaveRecipeUseCase(recipePort),
                unsave = RemoveSavedRecipeUseCase(recipePort),
                planCook = planCook,
                cook = CookRecipeUseCase(recipePort, planCook, consume),
                undoCook = UndoCookUseCase(pantryPort, time, noUnits()),
                addMissing =
                    AddMissingIngredientsToShoppingListUseCase(
                        recipePort,
                        pantryPort,
                        items,
                        catalogue,
                        sequentialIds(),
                        time,
                        noUnits(),
                    ),
                defaultList = EnsureDefaultShoppingListUseCase(lists, IdGenerator { "list-1" }, time),
            )
        return RecipeDetailViewModel(reads, writes, ObserveKitchenUseCase(kitchens))
            .also { it.start(user, recipeId, listOf("en"), "List") }
    }

    protected fun ingredient(
        id: String,
        labels: Map<String, String>,
    ): Ingredient = Ingredient(IngredientId.of(id).orFail(), labels, null, emptyList())

    protected fun term(
        ref: TermRef,
        labels: Map<String, String>,
    ): Term = Term(ref, labels, null, 0)

    protected fun unitsTaxonomy(default: String): Taxonomy =
        Taxonomy(gram.taxonomy, mapOf("en" to "Units"), default, TaxonomyPurpose.UNITS)

    /** No `purpose` at all — the same as diets, allergens and every other tag vocabulary seeded. */
    protected fun cuisinesTaxonomy(default: String): Taxonomy =
        Taxonomy(TaxonomyId.of("cuisines").orFail(), mapOf("en" to "Cuisines"), default, null)

    /** Distinct ids: a constant one would fold two drafted lines into a single item. */
    protected fun sequentialIds(): IdGenerator {
        var next = 0
        return IdGenerator { "shopping-${++next}" }
    }
}

internal fun <T> AppResult<T>.orFail(): T = (this as AppResult.Success).data
