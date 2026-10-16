package com.kitchenai.ui.presentation.pantry

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
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
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.pantry.AddPantryItemUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.pantry.RemovePantryItemUseCase
import com.kitchenai.shared.domain.usecase.pantry.UpdatePantryItemUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakeTaxonomyPort
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.noUnits
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_unauthorized_own_data
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pantry = FakePantryPort()
    private val catalogue = FakeIngredientPort()
    private val taxonomies = FakeTaxonomyPort()
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
    fun `a listener that fails before its first emission stops the spinner`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId, languageTags)
            advanceUntilIdle()

            // Cold start with no cache and no network: nothing has ever been emitted.
            pantry.errors.emit(AppError.Network())
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(false, state.isLoading)
            assertEquals(UiText.of(Res.string.error_no_connection), state.error)
        }

    @Test
    fun `a broken listener is reported without clearing the rows already on screen`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            pantry.errors.emit(AppError.Network())
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(UiText.of(Res.string.error_no_connection), state.error)
            assertEquals(1, state.items.size)
            assertEquals(false, state.isLoading)
        }

    @Test
    fun `a new item is written once`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.save(PantryItemDraft(ingredientId, null, 1.0, unitRef, null, null))
            advanceUntilIdle()

            val written = pantry.upserted.single()
            assertEquals(ingredientId, written.ingredient)
            assertEquals(1.0, written.quantity.amount)
        }

    @Test
    fun `a free-text item is written even though the catalogue has never heard of it`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.save(PantryItemDraft(null, "the good bread", 1.0, null, null, null))
            advanceUntilIdle()

            val written = pantry.upserted.single()
            assertNull(written.ingredient)
            assertEquals("the good bread", written.freeText)
        }

    @Test
    fun `editing a row writes it back under its own id`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            val row = viewModel.state.value.items.single()

            viewModel.openEditor(row)
            viewModel.save(PantryItemDraft(row.ingredient, null, 5.0, row.unit, null, null))
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
            viewModel.save(PantryItemDraft(other, null, 1.0, row.unit, null, null))
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
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)

            // A different listener speaking says nothing about the broken one.
            catalogue.emit(listOf(ingredient))
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)
        }

    @Test
    fun `a listener that emits again clears the banner it raised`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            pantry.errors.emit(AppError.Network())
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)

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
    fun `deleting from the editor closes it and removes the row with an undo`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            val row = viewModel.state.value.items.single()
            viewModel.openEditor(row)

            viewModel.events.test {
                viewModel.deleteEditing()
                advanceUntilIdle()
                assertEquals(itemId, (awaitItem() as PantryEvent.ItemRemoved).restore.id)
            }
            assertEquals(listOf(itemId), pantry.removed)
            assertFalse(viewModel.state.value.isEditorOpen)
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
                viewModel.save(PantryItemDraft(ingredientId, null, 1.0, unitRef, null, null))
                advanceUntilIdle()
                assertEquals(PantryEvent.SaveFailed(UNAUTHORIZED_MESSAGE), awaitItem())
            }
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `the catalogue options carry the default unit that fills an empty amount`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            val option = viewModel.state.value.ingredients.single()
            assertEquals(IngredientOption(ingredientId, INGREDIENT_LABEL, unitRef), option)
        }

    @Test
    fun `recent lists what the pantry touched last and nothing the catalogue lacks`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId, languageTags)
            catalogue.emit(listOf(ingredient, otherIngredient))
            taxonomies.terms.emit(listOf(unitTerm, locationTerm))
            pantry.items.emit(
                listOf(
                    item,
                    item.copy(
                        id = PantryItemId.of("item-3").value(),
                        ingredient = otherIngredient.id,
                        updatedAt = Instant.fromEpochSeconds(10),
                    ),
                    item.copy(
                        id = PantryItemId.of("item-4").value(),
                        ingredient = IngredientId.of("unknown").value(),
                        updatedAt = Instant.fromEpochSeconds(20),
                    ),
                    item.copy(id = PantryItemId.of("item-5").value(), ingredient = null, freeText = "bread"),
                ),
            )
            advanceUntilIdle()

            assertEquals(listOf(otherIngredient.id, ingredientId), viewModel.state.value.recent.map { it.id })
        }

    @Test
    fun `save and add another keeps the sheet open on an empty form and names what was added`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            viewModel.openEditor(null)

            viewModel.saveAndAddAnother(PantryItemDraft(ingredientId, null, 1.0, unitRef, null, null))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isEditorOpen)
            assertNull(state.editing)
            assertEquals(1, state.editorRevision)
            assertEquals(INGREDIENT_LABEL, state.lastAdded)
            assertEquals(ingredientId, pantry.upserted.single().ingredient)
        }

    @Test
    fun `five items in a row never close the sheet`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            viewModel.openEditor(null)

            repeat(5) { index ->
                viewModel.saveAndAddAnother(PantryItemDraft(null, "item $index", 1.0, null, null, null))
                advanceUntilIdle()
                assertTrue(viewModel.state.value.isEditorOpen)
            }

            assertEquals(5, viewModel.state.value.editorRevision)
            assertEquals(5, pantry.upserted.size)
        }

    @Test
    fun `a free text item is named as typed in the confirmation`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            viewModel.openEditor(null)

            viewModel.saveAndAddAnother(PantryItemDraft(null, "the good bread", 1.0, null, null, null))

            assertEquals("the good bread", viewModel.state.value.lastAdded)
        }

    @Test
    fun `a refused write takes the confirmation back`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            pantry.upsertResult = AppResult.Failure(AppError.Unauthorized())
            viewModel.openEditor(null)

            viewModel.events.test {
                viewModel.saveAndAddAnother(PantryItemDraft(ingredientId, null, 1.0, unitRef, null, null))
                advanceUntilIdle()
                assertEquals(PantryEvent.SaveFailed(UNAUTHORIZED_MESSAGE), awaitItem())
            }
            assertNull(viewModel.state.value.lastAdded)
        }

    @Test
    fun `closing the sheet forgets the confirmation`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            viewModel.openEditor(null)
            viewModel.saveAndAddAnother(PantryItemDraft(ingredientId, null, 1.0, unitRef, null, null))
            advanceUntilIdle()

            viewModel.closeEditor()

            assertNull(viewModel.state.value.lastAdded)
            assertFalse(viewModel.state.value.isEditorOpen)
        }

    @Test
    fun `the listeners are subscribed once however often the screen is composed`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.start(userId, languageTags)
            advanceUntilIdle()

            assertEquals(1, pantry.items.subscriptionCount.value)
        }

    @Test
    fun `a search narrows the rows and clearing it brings them back`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.search("nothing like it")
            assertEquals(emptyList(), viewModel.state.value.visible)

            viewModel.search("INGREDIENT")
            assertEquals(listOf(itemId), viewModel.state.value.visible.map { row -> row.id })

            viewModel.search("")
            assertEquals(viewModel.state.value.items, viewModel.state.value.visible)
        }

    @Test
    fun `a location chip filters and tapping it again turns it off`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            assertEquals(listOf(locationRef to LOCATION_LABEL), viewModel.state.value.filterLocations)

            viewModel.filterByLocation(locationRef)
            assertEquals(locationRef, viewModel.state.value.locationFilter)
            assertEquals(listOf(itemId), viewModel.state.value.visible.map { row -> row.id })

            viewModel.filterByLocation(locationRef)
            assertEquals(null, viewModel.state.value.locationFilter)
        }

    @Test
    fun `the filter follows the rows as they change`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            viewModel.search("other")
            assertEquals(emptyList(), viewModel.state.value.visible)

            catalogue.emit(listOf(ingredient, otherIngredient))
            pantry.items.emit(
                listOf(item, item.copy(id = PantryItemId.of("item-9").value(), ingredient = otherIngredient.id)),
            )
            advanceUntilIdle()

            assertEquals(listOf("item-9"), viewModel.state.value.visible.map { row -> row.id.value })
        }

    @Test
    fun `adding from a search that found nothing opens the sheet with what was typed`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            viewModel.search("  cumin ")

            viewModel.addFromSearch()

            assertTrue(viewModel.state.value.isEditorOpen)
            assertEquals(null, viewModel.state.value.editing)
            assertEquals("cumin", viewModel.state.value.prefill)

            viewModel.closeEditor()
            assertEquals(null, viewModel.state.value.prefill)
        }

    @Test
    fun `save and add another after adding from a search starts the next form empty`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            viewModel.search("cumin")
            viewModel.addFromSearch()

            viewModel.saveAndAddAnother(PantryItemDraft(null, "cumin", 1.0, null, null, null))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isEditorOpen)
            assertEquals(null, viewModel.state.value.prefill)
        }

    private suspend fun TestScope.loadedViewModel(): PantryViewModel {
        val viewModel = viewModel()
        viewModel.start(userId, languageTags)
        seed()
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun seed() {
        catalogue.emit(listOf(ingredient))
        taxonomies.terms.emit(listOf(unitTerm, locationTerm))
        pantry.items.emit(listOf(item))
    }

    private fun viewModel(): PantryViewModel {
        val time = TimeProvider { Instant.fromEpochSeconds(0) }
        return PantryViewModel(
            reads =
                PantryReadsDelegate(
                    pantry = ObservePantryUseCase(pantry),
                    ingredients = ObserveIngredientsUseCase(catalogue),
                    taxonomy = ObserveTaxonomyUseCase(taxonomies),
                    taxonomies = ObserveTaxonomiesUseCase(taxonomies),
                ),
            writes =
                PantryWritesDelegate(
                    add = AddPantryItemUseCase(pantry, IdGenerator { "item-2" }, time, noUnits()),
                    update = UpdatePantryItemUseCase(pantry, time),
                    remove = RemovePantryItemUseCase(pantry),
                    time = time,
                ),
            observeKitchen = ObserveKitchenUseCase(kitchens),
        )
    }
}

private const val INGREDIENT_LABEL = "ingredient-label"
private const val UNIT_LABEL = "unit-label"
private const val LOCATION_LABEL = "location-label"
private val UNAUTHORIZED_MESSAGE = UiText.of(Res.string.error_unauthorized_own_data)

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

private val otherIngredient =
    Ingredient(
        id = IngredientId.of("ingredient-2").value(),
        labels = mapOf("aa" to "other-label"),
        defaultUnit = null,
        tags = emptyList(),
    )

private val item =
    PantryItem(
        id = itemId,
        ingredient = ingredientId,
        freeText = null,
        quantity = Quantity(2.0, unitRef),
        location = locationRef,
        expiresAt = null,
        updatedAt = Instant.fromEpochSeconds(0),
    )

private val unitTerm = Term(unitRef, mapOf("aa" to UNIT_LABEL), null, 0)
private val locationTerm = Term(locationRef, mapOf("aa" to LOCATION_LABEL), null, 0)

private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data

private class FakePantryPort : PantryRepositoryContract {
    val items = MutableSharedFlow<List<PantryItem>>(replay = 1)
    val errors = MutableSharedFlow<AppError>()
    val upserted = mutableListOf<PantryItem>()
    val removed = mutableListOf<PantryItemId>()
    var upsertResult: AppResult<Unit> = AppResult.Success(Unit)

    override fun observePantry(kitchenId: KitchenId): Flow<List<PantryItem>> = items

    override fun pantryErrors(kitchenId: KitchenId): Flow<AppError> = errors

    // The read-modify-write use cases read this, never the listener above.
    override suspend fun getPantry(kitchenId: KitchenId): AppResult<List<PantryItem>> = AppResult.Success(emptyList())

    override suspend fun upsert(
        kitchenId: KitchenId,
        item: PantryItem,
    ): AppResult<Unit> {
        upserted += item
        return upsertResult
    }

    override suspend fun remove(
        kitchenId: KitchenId,
        id: PantryItemId,
    ): AppResult<Unit> {
        removed += id
        return AppResult.Success(Unit)
    }

    override suspend fun upsertAll(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ): AppResult<Unit> {
        upserted += items
        return upsertResult
    }

    override suspend fun upsertAllConfirmed(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ): AppResult<Unit> = upsertAll(kitchenId, items)
}

/** One stream of terms, served per taxonomy: a vocabulary must not answer for another one. */
