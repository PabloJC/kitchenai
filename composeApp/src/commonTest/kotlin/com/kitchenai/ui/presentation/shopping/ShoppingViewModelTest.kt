package com.kitchenai.ui.presentation.shopping

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
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
import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.ClearCheckedItemsUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase
import com.kitchenai.shared.domain.usecase.shopping.MoveCheckedItemsToPantryUseCase
import com.kitchenai.shared.domain.usecase.shopping.ObserveShoppingItemsUseCase
import com.kitchenai.shared.domain.usecase.shopping.RemoveShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemCheckedUseCase
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemQuantityUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeShoppingItemPort
import com.kitchenai.ui.presentation.common.FakeShoppingListPort
import com.kitchenai.ui.presentation.common.FakeTaxonomyPort
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.noUnits
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val lists = FakeShoppingListPort()
    private val items = FakeShoppingItemPort()
    private val catalogue = FakeIngredientPort()
    private val pantry = FakePantryPort()
    private val kitchens = FakeKitchenPort()

    // `viewModelScope` runs on Dispatchers.Main, absent outside an app.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the stream is split into the two sections the screen draws`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1"), line("item-2", checked = true)))
            advanceUntilIdle()

            assertEquals(listOf("item-1"), viewModel.state.value.unchecked.map { it.id.value })
            assertEquals(listOf("item-2"), viewModel.state.value.checked.map { it.id.value })
        }

    @Test
    fun `ticking a line writes once and moves nothing until the stream echoes it`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            viewModel.setChecked(itemId("item-1"), checked = true)
            advanceUntilIdle()

            assertEquals(1, items.upserts.size)
            assertEquals(true, items.upserts.single().single().checked)
            // The write is out; the state still renders what the listener last sent.
            assertEquals(listOf("item-1"), viewModel.state.value.unchecked.map { it.id.value })
            assertTrue(viewModel.state.value.checked.isEmpty())
        }

    @Test
    fun `clearing the ticked lines announces how many went`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1", checked = true), line("item-2", checked = true)))
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.clearChecked()
                advanceUntilIdle()

                assertEquals(ShoppingEvent.CheckedCleared(2), awaitItem())
                assertEquals(1, items.clears)
            }
        }

    @Test
    fun `moving checked lines announces how many moved and how many stayed`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(
                listOf(
                    line("item-1", checked = true, quantity = Quantity(200.0, gram)),
                    // No quantity: a normal shopping line, not a normal pantry row.
                    line("item-2", checked = true),
                ),
            )
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.moveCheckedToPantry()
                advanceUntilIdle()

                assertEquals(ShoppingEvent.MovedToPantry(moved = 1, skipped = 1), awaitItem())
                assertEquals(listOf(itemId("item-1")), items.removedBatch)
                assertEquals(listOf(ingredientId), pantry.held.map { it.ingredient })
            }
        }

    @Test
    fun `a checked line with no amount is counted as staying and is not removed`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1", freeText = "written by hand", checked = true)))
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.moveCheckedToPantry()
                advanceUntilIdle()

                assertEquals(ShoppingEvent.MovedToPantry(moved = 0, skipped = 1), awaitItem())
            }
            assertTrue(items.removedBatch.isEmpty())
        }

    @Test
    fun `a hand-typed line added with an amount carries it and then moves to the pantry`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(emptyList())
            advanceUntilIdle()

            viewModel.onDraftChange("something nobody catalogued")
            viewModel.onQuantityChange(2.0, gram)
            viewModel.add()
            advanceUntilIdle()

            val added = items.upserts.last().single()
            assertEquals("something nobody catalogued", added.freeText)
            assertEquals(Quantity(2.0, gram), added.quantity)

            items.emit(listOf(added.copy(checked = true)))
            advanceUntilIdle()
            viewModel.events.test {
                viewModel.moveCheckedToPantry()
                advanceUntilIdle()

                assertEquals(ShoppingEvent.MovedToPantry(moved = 1, skipped = 0), awaitItem())
            }
            assertEquals(Quantity(2.0, gram), pantry.held.single().quantity)
        }

    @Test
    fun `a line added without an amount stays bare and an unusable amount is dropped`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(emptyList())
            advanceUntilIdle()

            viewModel.onDraftChange("first")
            viewModel.add()
            advanceUntilIdle()
            assertNull(items.upserts.last().single().quantity)

            viewModel.onDraftChange("second")
            viewModel.onQuantityChange(0.0, gram)
            viewModel.add()
            advanceUntilIdle()
            assertNull(items.upserts.last().single().quantity)
        }

    @Test
    fun `adding clears the amount and moves the revision so the field starts empty`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(emptyList())
            advanceUntilIdle()
            val before = viewModel.state.value.draft.revision

            viewModel.onDraftChange("something")
            viewModel.onQuantityChange(3.0, gram)
            viewModel.add()
            advanceUntilIdle()

            val draft = viewModel.state.value.draft
            assertNull(draft.amount)
            assertEquals("", draft.text)
            assertEquals(before + 1, draft.revision)
        }

    @Test
    fun `typing after setting an amount keeps the amount`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(emptyList())
            advanceUntilIdle()

            viewModel.onQuantityChange(2.0, gram)
            viewModel.onDraftChange("milk")
            advanceUntilIdle()

            assertEquals(2.0, viewModel.state.value.draft.amount)
            assertEquals(gram, viewModel.state.value.draft.unit)
        }

    @Test
    fun `an existing line is given a quantity and the unit options reach the state`() =
        runTest(dispatcher) {
            val viewModel = started()
            taxonomies.taxonomies.emit(listOf(unitsTaxonomy))
            taxonomies.terms.emit(listOf(gramTerm))
            items.emit(listOf(line("item-1", freeText = "written by hand")))
            advanceUntilIdle()

            assertEquals(listOf(gram to "g"), viewModel.state.value.units)
            viewModel.setQuantity(itemId("item-1"), 500.0, gram)
            advanceUntilIdle()

            val written = items.upserts.last().single()
            assertEquals(itemId("item-1"), written.id)
            assertEquals(Quantity(500.0, gram), written.quantity)
        }

    @Test
    fun `a rejected amount edit is announced`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            viewModel.setQuantity(itemId("item-1"), 0.0, gram)
            advanceUntilIdle()

            assertTrue(items.upserts.isEmpty())
            assertTrue(viewModel.state.value.error != null)
        }

    @Test
    fun `a line exposes its amount and unit for the editor`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1", quantity = Quantity(400.0, gram))))
            advanceUntilIdle()

            val ui = viewModel.state.value.unchecked.single()
            assertEquals(400.0, ui.amount)
            assertEquals(gram, ui.unit)
        }

    @Test
    fun `a line from a dish is indistinguishable from one added by hand`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1", sourceRecipe = recipeId), line("item-2")))
            advanceUntilIdle()

            // The recipe id is a uuid minted on the device and resolves to no title anywhere, so
            // nothing about it may reach the row. Compared whole rather than field by field: any
            // future field carrying it would fail here rather than reach a reader.
            val (fromDish, byHand) = viewModel.state.value.unchecked
            assertEquals(byHand, fromDish.copy(id = byHand.id))
        }

    @Test
    fun `a quantity shows its unit rather than the term's identifier`() =
        runTest(dispatcher) {
            val viewModel = started()
            taxonomies.taxonomies.emit(listOf(unitsTaxonomy))
            taxonomies.terms.emit(listOf(gramTerm))
            items.emit(listOf(line("item-1", quantity = Quantity(400.0, gram))))
            advanceUntilIdle()

            assertEquals("400 g", viewModel.state.value.unchecked.single().quantity)
        }

    @Test
    fun `a listener failure surfaces without emptying the list`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            items.errors.emit(AppError.Network())
            advanceUntilIdle()

            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)
            assertEquals(1, viewModel.state.value.unchecked.size)
        }

    @Test
    fun `a picked suggestion is written as an identifier and typed words as free text`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(emptyList())
            catalogue.emit(listOf(Ingredient(ingredientId, mapOf("en" to "catalogued"), null, emptyList())))
            advanceUntilIdle()

            viewModel.onDraftChange("cata")
            // The state is derived from its sources, so it lands on the next turn, not inline.
            advanceUntilIdle()
            val suggestion = viewModel.state.value.draft.suggestions.single()
            assertEquals(ingredientId, suggestion.id)
            viewModel.onPick(suggestion)
            viewModel.add()
            advanceUntilIdle()

            val fromCatalogue = items.upserts.last().single()
            assertEquals(ingredientId, fromCatalogue.ingredient)
            assertNull(fromCatalogue.freeText)

            viewModel.onDraftChange("something nobody catalogued")
            viewModel.add()
            advanceUntilIdle()

            val freeText = items.upserts.last().single()
            assertEquals("something nobody catalogued", freeText.freeText)
            assertNull(freeText.ingredient)
        }

    @Test
    fun `an undone removal puts the line back`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1", freeText = "written by hand")))
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.remove(itemId("item-1"))
                advanceUntilIdle()
                val removed = awaitItem() as ShoppingEvent.ItemRemoved

                viewModel.undoRemove(removed.restore)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(itemId("item-1"), items.removed)
            assertEquals("written by hand", items.upserts.single().single().freeText)
        }

    @Test
    fun `a rejected write is announced and the next write that lands clears it`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            items.upsertResult = AppResult.Failure(AppError.Network())
            viewModel.setChecked(itemId("item-1"), checked = true)
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)

            items.upsertResult = AppResult.Success(Unit)
            viewModel.setChecked(itemId("item-1"), checked = false)
            advanceUntilIdle()
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `each stream keeps its own error and clears only its own`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            catalogue.errors.emit(AppError.Network())
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)

            // An item emission is not the catalogue recovering, so the banner stays.
            items.emit(listOf(line("item-1"), line("item-2")))
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)

            catalogue.emit(emptyList())
            advanceUntilIdle()
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `an item listener that fails before emitting says so instead of showing an empty list`() =
        runTest(dispatcher) {
            val viewModel = started()

            items.errors.emit(AppError.Unauthorized())
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(false, state.isLoading)
            assertTrue(state.failedToLoad)
        }

    @Test
    fun `a broken catalogue does not turn a list that never loaded into an empty one`() =
        runTest(dispatcher) {
            val viewModel = started()

            catalogue.errors.emit(AppError.Network())
            advanceUntilIdle()

            // "Nothing to buy" is a different sentence from "this has not loaded".
            assertTrue(viewModel.state.value.isLoading)
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)
        }

    private val taxonomies = FakeTaxonomyPort()

    private fun TestScope.started(): ShoppingViewModel {
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
                    ),
                writes =
                    ShoppingWritesDelegate(
                        add = AddShoppingItemUseCase(items, IdGenerator { "added-${++generated}" }, time, noUnits()),
                        setChecked = SetShoppingItemCheckedUseCase(items, time),
                        setQuantity = SetShoppingItemQuantityUseCase(items, time),
                        remove = RemoveShoppingItemUseCase(items),
                        clearChecked = ClearCheckedItemsUseCase(items),
                        moveCheckedToPantry =
                            MoveCheckedItemsToPantryUseCase(
                                items,
                                pantry,
                                IdGenerator { "moved-${++generated}" },
                                time,
                                noUnits(),
                            ),
                    ),
            )
        viewModel.start(userId, listOf("en"), "list")
        advanceUntilIdle()
        return viewModel
    }
}

private val userId = unwrap(UserId.of("user-1"))
private val ingredientId = unwrap(IngredientId.of("ing-1"))
private val recipeId = unwrap(RecipeId.of("recipe-1"))
private val gram = TermRef(unwrap(TaxonomyId.of("units")), unwrap(TermId.of("gram")))
private val gramTerm = Term(gram, mapOf("en" to "g"), null, 0)
private val unitsTaxonomy = Taxonomy(gram.taxonomy, mapOf("en" to "Units"), "en", TaxonomyPurpose.UNITS)

private fun itemId(raw: String): ShoppingItemId = unwrap(ShoppingItemId.of(raw))

private fun <T> unwrap(result: AppResult<T>): T = (result as AppResult.Success).data

/** Identifiers and opaque words only: a real product name here would be the constant the domain refuses. */
private fun line(
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

/** Everything on the one test dispatcher, so `advanceUntilIdle` drives the whole ViewModel. */
