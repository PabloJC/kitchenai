package com.kitchenai.ui.presentation.shopping

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The add field: the one-line parse, amounts, suggestions and what is written. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingAddViewModelTest : ShoppingViewModelHarness() {
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

                assertEquals(ShoppingEvent.MovedToPantry(moved = 1, skipped = 0), awaitMove().copy(undo = null))
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
    fun `an amount and unit typed before the name are added as the quantity`() =
        runTest(dispatcher) {
            val viewModel = started()
            taxonomies.taxonomies.emit(listOf(unitsTaxonomy))
            taxonomies.terms.emit(listOf(gramTerm))
            items.emit(emptyList())
            advanceUntilIdle()

            viewModel.onDraftChange("200 g flour")
            viewModel.add()
            advanceUntilIdle()

            val added = items.upserts.last().single()
            assertEquals("flour", added.freeText)
            assertEquals(Quantity(200.0, gram), added.quantity)
        }

    @Test
    fun `an amount set by hand wins over one typed in the field`() =
        runTest(dispatcher) {
            val viewModel = started()
            taxonomies.taxonomies.emit(listOf(unitsTaxonomy))
            taxonomies.terms.emit(listOf(gramTerm))
            items.emit(emptyList())
            advanceUntilIdle()

            viewModel.onDraftChange("200 g flour")
            viewModel.onQuantityChange(5.0, null)
            viewModel.add()
            advanceUntilIdle()

            assertEquals(Quantity(5.0, null), items.upserts.last().single().quantity)
        }

    @Test
    fun `a typed amount survives picking the suggestion its name found`() =
        runTest(dispatcher) {
            val viewModel = started()
            taxonomies.taxonomies.emit(listOf(unitsTaxonomy))
            taxonomies.terms.emit(listOf(gramTerm))
            items.emit(emptyList())
            catalogue.emit(listOf(Ingredient(ingredientId, mapOf("en" to "catalogued"), null, emptyList())))
            advanceUntilIdle()

            viewModel.onDraftChange("300 g cata")
            advanceUntilIdle()
            viewModel.onPick(viewModel.state.value.draft.suggestions.single())
            viewModel.add()
            advanceUntilIdle()

            val added = items.upserts.last().single()
            assertEquals(ingredientId, added.ingredient)
            assertEquals(Quantity(300.0, gram), added.quantity)
        }

    @Test
    fun `a typed name that is exactly a catalogue entry is added as that entry`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(emptyList())
            catalogue.emit(listOf(Ingredient(ingredientId, mapOf("en" to "Catalogued"), null, emptyList())))
            advanceUntilIdle()

            viewModel.onDraftChange("2 catalogued")
            viewModel.add()
            advanceUntilIdle()

            val added = items.upserts.last().single()
            assertEquals(ingredientId, added.ingredient)
            assertNull(added.freeText)
            assertEquals(Quantity(2.0, null), added.quantity)
        }

    @Test
    fun `a typed name that only starts like a catalogue entry stays free text`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(emptyList())
            catalogue.emit(listOf(Ingredient(ingredientId, mapOf("en" to "Catalogued"), null, emptyList())))
            advanceUntilIdle()

            viewModel.onDraftChange("catalog")
            viewModel.add()
            advanceUntilIdle()

            val added = items.upserts.last().single()
            assertNull(added.ingredient)
            assertEquals("catalog", added.freeText)
        }

    @Test
    fun `a suggestion already on the list says so`() =
        runTest(dispatcher) {
            val viewModel = started()
            catalogue.emit(listOf(Ingredient(ingredientId, mapOf("en" to "catalogued"), null, emptyList())))
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            viewModel.onDraftChange("cata")
            advanceUntilIdle()

            assertTrue(viewModel.state.value.draft.suggestions.single().onList)
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
}
