package com.kitchenai.ui.presentation.shopping

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.IngredientPort
import com.kitchenai.shared.domain.port.ShoppingItemPort
import com.kitchenai.shared.domain.port.ShoppingListPort
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItem
import com.kitchenai.shared.domain.usecase.shopping.ClearCheckedItems
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingList
import com.kitchenai.shared.domain.usecase.shopping.ObserveShoppingItems
import com.kitchenai.shared.domain.usecase.shopping.RemoveShoppingItem
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemChecked
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
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
    fun `a listener failure surfaces without emptying the list`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            items.errors.emit(AppError.Network())
            advanceUntilIdle()

            assertEquals("No connection", viewModel.state.value.error)
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

    private fun TestScope.started(): ShoppingViewModel {
        val time = TimeProvider { Instant.fromEpochSeconds(0) }
        var generated = 0
        val viewModel =
            ShoppingViewModel(
                ensureDefaultShoppingList = EnsureDefaultShoppingList(lists, IdGenerator { "list-1" }, time),
                reads =
                    ShoppingReads(
                        items = ObserveShoppingItems(items),
                        ingredients = ObserveIngredients(catalogue),
                    ),
                writes =
                    ShoppingWrites(
                        add = AddShoppingItem(items, IdGenerator { "added-${++generated}" }, time),
                        setChecked = SetShoppingItemChecked(items, time),
                        remove = RemoveShoppingItem(items),
                        clearChecked = ClearCheckedItems(items),
                    ),
                dispatchers = TestDispatcherProvider(dispatcher),
            )
        viewModel.start(userId, listOf("en"), "list")
        advanceUntilIdle()
        return viewModel
    }
}

private val userId = unwrap(UserId.of("user-1"))
private val listId = unwrap(ShoppingListId.of("list-1"))
private val ingredientId = unwrap(IngredientId.of("ing-1"))

private fun itemId(raw: String): ShoppingItemId = unwrap(ShoppingItemId.of(raw))

private fun <T> unwrap(result: AppResult<T>): T = (result as AppResult.Success).data

/** Identifiers and opaque words only: a real product name here would be the constant the domain refuses. */
private fun line(
    raw: String,
    checked: Boolean = false,
    freeText: String? = null,
): ShoppingItem =
    ShoppingItem(
        id = itemId(raw),
        ingredient = if (freeText == null) ingredientId else null,
        freeText = freeText,
        quantity = null,
        checked = checked,
        sourceRecipe = null,
        updatedAt = Instant.fromEpochSeconds(0),
    )

/** The list already exists, which is the state the screen opens in: the session gate created it. */
private class FakeShoppingListPort : ShoppingListPort {
    override fun observeLists(userId: UserId): Flow<List<ShoppingList>> = emptyFlow()

    override fun listErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getLists(userId: UserId): AppResult<List<ShoppingList>> =
        AppResult.Success(listOf(ShoppingList(listId, userId, emptyMap(), Instant.fromEpochSeconds(0))))

    override suspend fun upsertList(
        userId: UserId,
        list: ShoppingList,
    ): AppResult<Unit> = AppResult.Success(Unit)
}

/**
 * Writes are recorded and never echoed: what the ViewModel renders has to come from the stream,
 * so a test that wrote back would hide exactly the bug this screen can have.
 */
private class FakeShoppingItemPort : ShoppingItemPort {
    private val stream = MutableSharedFlow<List<ShoppingItem>>(replay = 1)
    private var current: List<ShoppingItem> = emptyList()

    val errors = MutableSharedFlow<AppError>()
    val upserts = mutableListOf<List<ShoppingItem>>()
    var removed: ShoppingItemId? = null
    var clears = 0

    suspend fun emit(items: List<ShoppingItem>) {
        current = items
        stream.emit(items)
    }

    override fun observeItems(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>> = stream

    override fun itemErrors(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<AppError> = errors

    override suspend fun getItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<List<ShoppingItem>> = AppResult.Success(current)

    override suspend fun upsertItems(
        userId: UserId,
        listId: ShoppingListId,
        items: List<ShoppingItem>,
    ): AppResult<Unit> {
        upserts += items
        return AppResult.Success(Unit)
    }

    override suspend fun removeItem(
        userId: UserId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
    ): AppResult<Unit> {
        removed = itemId
        return AppResult.Success(Unit)
    }

    override suspend fun removeCheckedItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<Unit> {
        clears++
        return AppResult.Success(Unit)
    }
}

private class FakeIngredientPort : IngredientPort {
    private val stream = MutableSharedFlow<List<Ingredient>>(replay = 1)

    suspend fun emit(ingredients: List<Ingredient>) {
        stream.emit(ingredients)
    }

    override fun observeIngredients(): Flow<List<Ingredient>> = stream

    override fun ingredientErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getIngredient(id: IngredientId): AppResult<Ingredient> =
        AppResult.Failure(AppError.NotFound("ingredient"))
}

/** Everything on the one test dispatcher, so `advanceUntilIdle` drives the whole ViewModel. */
private class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}
