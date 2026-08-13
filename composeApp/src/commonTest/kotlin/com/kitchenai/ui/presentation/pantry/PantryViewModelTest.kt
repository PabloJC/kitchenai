package com.kitchenai.ui.presentation.pantry

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.IngredientPort
import com.kitchenai.shared.domain.port.PantryPort
import com.kitchenai.shared.domain.port.TaxonomyPort
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.AddPantryItem
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.pantry.ObservePantry
import com.kitchenai.shared.domain.usecase.pantry.RemovePantryItem
import com.kitchenai.shared.domain.usecase.pantry.UpdatePantryItem
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomies
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
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
class PantryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pantry = FakePantryPort()
    private val catalogue = FakeIngredientPort()
    private val taxonomies = FakeTaxonomyPort()

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
    fun `nothing is a pantry until the listener emits one`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.state.test {
                assertTrue(awaitItem().isLoading)
                viewModel.start(userId, languageTags)
                advanceUntilIdle()
                // Subscribing emits nothing: a listener that has not spoken yet is still loading,
                // and an unchanged state is not a new emission.
                expectNoEvents()
                assertTrue(viewModel.state.value.isLoading)

                seed()
                advanceUntilIdle()
                val loaded = expectMostRecentItem()
                assertEquals(false, loaded.isLoading)
                assertEquals(listOf(INGREDIENT_LABEL), loaded.items.map { row -> row.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a row is resolved into words and the option lists come from the catalogue`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            val state = viewModel.state.value
            val row = state.items.single()
            assertEquals("2 $UNIT_LABEL", row.quantityLabel)
            assertEquals(LOCATION_LABEL, row.locationLabel)
            assertEquals(listOf(unitRef to UNIT_LABEL), state.units)
            assertEquals(listOf(locationRef to LOCATION_LABEL), state.locations)
        }

    @Test
    fun `a fresh pantry can still offer a storage location`() =
        runTest(dispatcher) {
            // Nothing held, so nothing to derive a location taxonomy from: the catalogue
            // declaring one is the only reason this picker has anything in it.
            val viewModel = viewModel()
            viewModel.start(userId, languageTags)
            taxonomies.taxonomies.emit(
                listOf(
                    Taxonomy(
                        id = locationRef.taxonomy,
                        labels = mapOf("aa" to "Places"),
                        purpose = TaxonomyPurpose.STORAGE_LOCATIONS,
                    ),
                ),
            )
            taxonomies.terms.emit(listOf(locationTerm))
            pantry.items.emit(emptyList())
            advanceUntilIdle()

            assertEquals(listOf(locationRef to LOCATION_LABEL), viewModel.state.value.locations)
        }

    @Test
    fun `an identifier the catalogue cannot name is rendered as itself`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId, languageTags)
            pantry.items.emit(listOf(item))
            advanceUntilIdle()

            assertEquals(ingredientId.value, viewModel.state.value.items.single().name)
        }

    @Test
    fun `a broken listener is reported without clearing the rows already on screen`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            pantry.errors.emit(AppError.Network())
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("No connection", state.error)
            assertEquals(1, state.items.size)
            assertEquals(false, state.isLoading)
        }

    @Test
    fun `a new item is written once`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.save(PantryItemDraft(ingredientId, 1.0, unitRef, null, null))
            advanceUntilIdle()

            val written = pantry.upserted.single()
            assertEquals(ingredientId, written.ingredient)
            assertEquals(1.0, written.quantity.amount)
        }

    @Test
    fun `editing a row writes it back under its own id`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            val row = viewModel.state.value.items.single()

            viewModel.openEditor(row)
            viewModel.save(PantryItemDraft(row.ingredient, 5.0, row.unit, null, null))
            advanceUntilIdle()

            val written = pantry.upserted.single()
            assertEquals(itemId, written.id)
            assertEquals(5.0, written.quantity.amount)
            assertNull(viewModel.state.value.editing)
        }

    @Test
    fun `editing the ingredient of a row writes the new one`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            val row = viewModel.state.value.items.single()
            val other = IngredientId.of("ingredient-2").value()

            viewModel.openEditor(row)
            viewModel.save(PantryItemDraft(other, 1.0, row.unit, null, null))
            advanceUntilIdle()

            val written = pantry.upserted.single()
            assertEquals(itemId, written.id)
            assertEquals(other, written.ingredient)
        }

    @Test
    fun `a catalogue that recovers does not clear a pantry that has not`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            pantry.errors.emit(AppError.Network())
            advanceUntilIdle()
            assertEquals("No connection", viewModel.state.value.error)

            // A different listener speaking says nothing about the broken one.
            catalogue.ingredients.emit(listOf(ingredient))
            advanceUntilIdle()
            assertEquals("No connection", viewModel.state.value.error)
        }

    @Test
    fun `a listener that emits again clears the banner it raised`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            pantry.errors.emit(AppError.Network())
            advanceUntilIdle()
            assertEquals("No connection", viewModel.state.value.error)

            pantry.items.emit(listOf(item))
            advanceUntilIdle()
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `removing a row announces it so that it can be undone`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            val row = viewModel.state.value.items.single()

            viewModel.events.test {
                viewModel.remove(row)
                advanceUntilIdle()
                val removed = awaitItem() as PantryEvent.ItemRemoved
                assertEquals(INGREDIENT_LABEL, removed.name)
                assertEquals(itemId, removed.restore.id)
            }
            assertEquals(listOf(itemId), pantry.removed)
        }

    @Test
    fun `undo restores the removed row rather than adding a second holding`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            val row = viewModel.state.value.items.single()

            viewModel.events.test {
                viewModel.remove(row)
                advanceUntilIdle()
                val removed = awaitItem() as PantryEvent.ItemRemoved

                viewModel.undoRemove(removed.restore)
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(itemId, pantry.upserted.single().id)
        }

    @Test
    fun `a rejected write is announced as an event and never as state`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            pantry.upsertResult = AppResult.Failure(AppError.Unauthorized())

            viewModel.events.test {
                viewModel.save(PantryItemDraft(ingredientId, 1.0, unitRef, null, null))
                advanceUntilIdle()
                assertEquals(PantryEvent.SaveFailed(UNAUTHORIZED_MESSAGE), awaitItem())
            }
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `the listeners are subscribed once however often the screen is composed`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.start(userId, languageTags)
            advanceUntilIdle()

            assertEquals(1, pantry.items.subscriptionCount.value)
        }

    private suspend fun TestScope.loadedViewModel(): PantryViewModel {
        val viewModel = viewModel()
        viewModel.start(userId, languageTags)
        seed()
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun seed() {
        catalogue.ingredients.emit(listOf(ingredient))
        taxonomies.terms.emit(listOf(unitTerm, locationTerm))
        pantry.items.emit(listOf(item))
    }

    private fun viewModel(): PantryViewModel {
        val time = TimeProvider { Instant.fromEpochSeconds(0) }
        return PantryViewModel(
            reads =
                PantryReads(
                    pantry = ObservePantry(pantry),
                    ingredients = ObserveIngredients(catalogue),
                    taxonomy = ObserveTaxonomy(taxonomies),
                    taxonomies = ObserveTaxonomies(taxonomies),
                ),
            writes =
                PantryWrites(
                    add = AddPantryItem(pantry, IdGenerator { "item-2" }, time),
                    update = UpdatePantryItem(pantry, time),
                    remove = RemovePantryItem(pantry),
                    time = time,
                ),
            dispatchers = TestDispatcherProvider(dispatcher),
        )
    }
}

private const val INGREDIENT_LABEL = "ingredient-label"
private const val UNIT_LABEL = "unit-label"
private const val LOCATION_LABEL = "location-label"
private const val UNAUTHORIZED_MESSAGE = "This account is not allowed to read its own data"

private val languageTags = listOf("aa")
private val userId = UserId.of("user-1").value()
private val ingredientId = IngredientId.of("ingredient-1").value()
private val itemId = PantryItemId.of("item-1").value()
private val unitRef = TermRef(TaxonomyId.of("taxonomy-1").value(), TermId.of("term-1").value())
private val locationRef = TermRef(TaxonomyId.of("taxonomy-2").value(), TermId.of("term-2").value())

private val ingredient =
    Ingredient(
        id = ingredientId,
        labels = mapOf("aa" to INGREDIENT_LABEL),
        defaultUnit = unitRef,
        tags = emptyList(),
    )

private val item =
    PantryItem(
        id = itemId,
        ingredient = ingredientId,
        quantity = Quantity(2.0, unitRef),
        location = locationRef,
        expiresAt = null,
        updatedAt = Instant.fromEpochSeconds(0),
    )

private val unitTerm = Term(unitRef, mapOf("aa" to UNIT_LABEL), null, 0)
private val locationTerm = Term(locationRef, mapOf("aa" to LOCATION_LABEL), null, 0)

private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data

private class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}

private class FakePantryPort : PantryPort {
    val items = MutableSharedFlow<List<PantryItem>>(replay = 1)
    val errors = MutableSharedFlow<AppError>()
    val upserted = mutableListOf<PantryItem>()
    val removed = mutableListOf<PantryItemId>()
    var upsertResult: AppResult<Unit> = AppResult.Success(Unit)

    override fun observePantry(userId: UserId): Flow<List<PantryItem>> = items

    override fun pantryErrors(userId: UserId): Flow<AppError> = errors

    // The read-modify-write use cases read this, never the listener above.
    override suspend fun getPantry(userId: UserId): AppResult<List<PantryItem>> = AppResult.Success(emptyList())

    override suspend fun upsert(
        userId: UserId,
        item: PantryItem,
    ): AppResult<Unit> {
        upserted += item
        return upsertResult
    }

    override suspend fun remove(
        userId: UserId,
        id: PantryItemId,
    ): AppResult<Unit> {
        removed += id
        return AppResult.Success(Unit)
    }

    override suspend fun upsertAll(
        userId: UserId,
        items: List<PantryItem>,
    ): AppResult<Unit> {
        upserted += items
        return upsertResult
    }
}

private class FakeIngredientPort : IngredientPort {
    val ingredients = MutableSharedFlow<List<Ingredient>>(replay = 1)

    override fun observeIngredients(): Flow<List<Ingredient>> = ingredients

    override fun ingredientErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getIngredient(id: IngredientId): AppResult<Ingredient> =
        AppResult.Failure(AppError.NotFound("Ingredient"))
}

/** One stream of terms, served per taxonomy: a vocabulary must not answer for another one. */
private class FakeTaxonomyPort : TaxonomyPort {
    val terms = MutableSharedFlow<List<Term>>(replay = 1)

    // Replay without an initial value: a listener that has not answered emits nothing, and a
    // fake that emits an empty catalogue on subscribe hides the difference.
    val taxonomies = MutableSharedFlow<List<Taxonomy>>(replay = 1)

    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> =
        terms.map { known -> known.filter { term -> term.ref.taxonomy == id } }

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = taxonomies

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = emptyFlow()

    override fun taxonomiesErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> = AppResult.Success(emptyList())
}
