package com.kitchenai.ui.presentation.shopping

import app.cash.turbine.ReceiveTurbine
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.shared.domain.usecase.recipe.GetStoredSuggestionsUseCase
import com.kitchenai.shared.domain.usecase.recipe.ObserveSavedRecipesUseCase
import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.ClearCheckedItemsUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.MoveCheckedItemsToPantryUseCase
import com.kitchenai.shared.domain.usecase.shopping.ObserveShoppingItemsUseCase
import com.kitchenai.shared.domain.usecase.shopping.RemoveShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemCheckedUseCase
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemQuantityUseCase
import com.kitchenai.shared.domain.usecase.shopping.UndoMoveToPantryUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeRecipePort
import com.kitchenai.ui.presentation.common.FakeShoppingItemPort
import com.kitchenai.ui.presentation.common.FakeShoppingListPort
import com.kitchenai.ui.presentation.common.FakeTaxonomyPort
import com.kitchenai.ui.presentation.common.noUnits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Instant

/** The ViewModel wired to fakes, shared by the shopping tests so each class stays one subject. */
@OptIn(ExperimentalCoroutinesApi::class)
open class ShoppingViewModelHarness {
    // Everything on the one test dispatcher, so `advanceUntilIdle` drives the whole ViewModel.
    protected val dispatcher = StandardTestDispatcher()
    protected val lists = FakeShoppingListPort()
    protected val items = FakeShoppingItemPort()
    protected val catalogue = FakeIngredientPort()
    protected val pantry = FakePantryPort()
    protected val kitchens = FakeKitchenPort()
    protected val recipes = FakeRecipePort()

    // `viewModelScope` runs on Dispatchers.Main, absent outside an app.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected val taxonomies = FakeTaxonomyPort()

    protected fun TestScope.started(): ShoppingViewModel {
        val time = TimeProvider { Instant.fromEpochSeconds(0) }
        var generated = 0
        val viewModel =
            ShoppingViewModel(
                ensureDefaultShoppingList = EnsureDefaultShoppingListUseCase(lists, IdGenerator { "list-1" }, time),
                observeKitchen = ObserveKitchenUseCase(kitchens),
                reads =
                    ShoppingReadsDelegate(
                        items = ObserveShoppingItemsUseCase(items),
                        ingredients = ObserveIngredientsUseCase(catalogue),
                        taxonomies = ObserveTaxonomiesUseCase(taxonomies),
                        taxonomy = ObserveTaxonomyUseCase(taxonomies),
                        savedRecipes = ObserveSavedRecipesUseCase(recipes),
                        storedRecipes = GetStoredSuggestionsUseCase(recipes, pantry, time, noUnits()),
                    ),
                writes =
                    ShoppingWritesDelegate(
                        add = AddShoppingItemUseCase(items, IdGenerator { "added-${++generated}" }, time, noUnits()),
                        setChecked = SetShoppingItemCheckedUseCase(items, time),
                        setQuantity = SetShoppingItemQuantityUseCase(items, time),
                        remove = RemoveShoppingItemUseCase(items),
                        clearChecked = ClearCheckedItemsUseCase(items),
                        undoMoveToPantry = UndoMoveToPantryUseCase(items, pantry),
                        moveCheckedToPantry =
                            MoveCheckedItemsToPantryUseCase(
                                items,
                                pantry,
                                IdGenerator { "moved-${++generated}" },
                                time,
                                noUnits(),
                                catalogue,
                            ),
                    ),
            )
        viewModel.start(userId, listOf("en"), "list")
        advanceUntilIdle()
        return viewModel
    }
}

internal val userId = unwrap(UserId.of("user-1"))
internal val ingredientId = unwrap(IngredientId.of("ing-1"))
internal val recipeId = unwrap(RecipeId.of("recipe-1"))
internal val gram = TermRef(unwrap(TaxonomyId.of("units")), unwrap(TermId.of("gram")))
internal val gramTerm = Term(gram, mapOf("en" to "g"), null, 0)
internal val unitsTaxonomy = Taxonomy(gram.taxonomy, mapOf("en" to "Units"), "en", TaxonomyPurpose.UNITS)

internal fun itemId(raw: String): ShoppingItemId = unwrap(ShoppingItemId.of(raw))

internal fun <T> unwrap(result: AppResult<T>): T = (result as AppResult.Success).data

internal suspend fun ReceiveTurbine<ShoppingEvent>.awaitMove(): ShoppingEvent.MovedToPantry =
    awaitItem() as ShoppingEvent.MovedToPantry

/** Identifiers and opaque words only: a real product name here would be the constant the domain refuses. */
internal fun line(
    raw: String,
    checked: Boolean = false,
    freeText: String? = null,
    quantity: Quantity? = null,
    sourceRecipe: RecipeId? = null,
): ShoppingItem =
    ShoppingItem(
        id = itemId(raw),
        ingredient = if (freeText == null) ingredientId else null,
        freeText = freeText,
        quantity = quantity,
        checked = checked,
        sourceRecipe = sourceRecipe,
        updatedAt = Instant.fromEpochSeconds(0),
    )
