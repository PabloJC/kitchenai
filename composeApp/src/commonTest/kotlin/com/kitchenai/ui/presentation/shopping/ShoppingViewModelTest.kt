package com.kitchenai.ui.presentation.shopping

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.MoveToPantryOptions
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest : ShoppingViewModelHarness() {
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
    fun `a ticked line keeps its row until the settle delay ends and then drops to the cart`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1"), line("item-2"), line("item-3")))
            advanceUntilIdle()

            viewModel.setChecked(itemId("item-2"), checked = true)
            items.emit(listOf(line("item-1"), line("item-2", checked = true), line("item-3")))
            advanceTimeBy(SETTLE_MILLIS - 1)
            runCurrent()

            val settling = viewModel.state.value
            // The stream sorts a ticked line last; the row must not follow it before the delay ends.
            assertEquals(listOf("item-1", "item-2", "item-3"), settling.unchecked.map { it.id.value })
            assertEquals(true, settling.unchecked[1].checked)
            assertTrue(settling.checked.isEmpty())

            advanceTimeBy(2)
            runCurrent()

            val settled = viewModel.state.value
            assertEquals(listOf("item-1", "item-3"), settled.unchecked.map { it.id.value })
            assertEquals(listOf("item-2"), settled.checked.map { it.id.value })
        }

    @Test
    fun `unticking a settling line puts it back at once and it never drops`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            viewModel.setChecked(itemId("item-1"), checked = true)
            items.emit(listOf(line("item-1", checked = true)))
            advanceTimeBy(SETTLE_MILLIS / 2)
            viewModel.setChecked(itemId("item-1"), checked = false)
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf("item-1"), state.unchecked.map { it.id.value })
            assertEquals(false, state.unchecked.single().checked)
            assertTrue(state.checked.isEmpty())
        }

    @Test
    fun `ticking again restarts the delay instead of keeping the first one`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            viewModel.setChecked(itemId("item-1"), checked = true)
            items.emit(listOf(line("item-1", checked = true)))
            advanceTimeBy(SETTLE_MILLIS - 100)
            viewModel.setChecked(itemId("item-1"), checked = false)
            items.emit(listOf(line("item-1")))
            runCurrent()
            viewModel.setChecked(itemId("item-1"), checked = true)
            items.emit(listOf(line("item-1", checked = true)))
            // Past the first delay but inside the second one.
            advanceTimeBy(200)
            runCurrent()

            assertEquals(listOf("item-1"), viewModel.state.value.unchecked.map { it.id.value })

            advanceTimeBy(SETTLE_MILLIS)
            runCurrent()

            assertTrue(viewModel.state.value.unchecked.isEmpty())
            assertEquals(listOf("item-1"), viewModel.state.value.checked.map { it.id.value })
        }

    @Test
    fun `a line ticked on another device goes straight to the cart`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1", checked = true)))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.unchecked.isEmpty())
            assertEquals(listOf("item-1"), viewModel.state.value.checked.map { it.id.value })
        }

    @Test
    fun `the ticked counts span lines still settling and lines in the cart`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(
                listOf(
                    line("item-1", quantity = Quantity(200.0, gram)),
                    line("item-2", checked = true, quantity = Quantity(1.0, gram)),
                    line("item-3", checked = true),
                ),
            )
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.tickedCount)
            assertEquals(listOf("item-2", "item-3"), viewModel.state.value.ticked.map { it.id.value })

            viewModel.setChecked(itemId("item-1"), checked = true)
            items.emit(
                listOf(
                    line("item-1", checked = true, quantity = Quantity(200.0, gram)),
                    line("item-2", checked = true, quantity = Quantity(1.0, gram)),
                    line("item-3", checked = true),
                ),
            )
            advanceTimeBy(1)
            runCurrent()

            // item-1 is still in "To buy" for the delay, and still counts as ticked.
            assertEquals(3, viewModel.state.value.tickedCount)
        }

    @Test
    fun `nothing ticked means nothing to move`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()

            assertEquals(0, viewModel.state.value.tickedCount)
            assertTrue(viewModel.state.value.ticked.isEmpty())
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

                assertEquals(ShoppingEvent.MovedToPantry(moved = 1, skipped = 1), awaitMove().copy(undo = null))
                assertEquals(listOf(itemId("item-1")), items.removedBatch)
                assertEquals(listOf(ingredientId), pantry.held.map { it.ingredient })
            }
        }

    @Test
    fun `undoing a move puts the line back on the list and takes the new row out of the pantry`() =
        runTest(dispatcher) {
            val viewModel = started()
            val moved = line("item-1", checked = true, quantity = Quantity(200.0, gram))
            items.emit(listOf(moved))
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.moveCheckedToPantry()
                advanceUntilIdle()
                val record = checkNotNull(awaitMove().undo)

                viewModel.undoMove(record)
                advanceUntilIdle()
            }

            assertEquals(listOf(moved), items.upserts.last())
            assertTrue(pantry.held.isEmpty())
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
    fun `a reviewed move dates its lines and takes a bare line along as one`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(
                listOf(
                    line("item-1", checked = true, quantity = Quantity(200.0, gram)),
                    line("item-2", freeText = "written by hand", checked = true),
                ),
            )
            advanceUntilIdle()
            val date = Instant.fromEpochSeconds(86_400)

            viewModel.events.test {
                viewModel.moveCheckedToPantry(
                    MoveToPantryOptions(expiresAt = mapOf(itemId("item-1") to date), defaultAmountForBare = true),
                )
                advanceUntilIdle()

                assertEquals(ShoppingEvent.MovedToPantry(moved = 2, skipped = 0), awaitMove().copy(undo = null))
            }
            assertEquals(setOf(itemId("item-1"), itemId("item-2")), items.removedBatch.toSet())
            assertEquals(Quantity(1.0), pantry.held.single { it.freeText == "written by hand" }.quantity)
            assertEquals(date, pantry.held.single { it.ingredient != null }.expiresAt)
        }

    @Test
    fun `undoing a reviewed move puts the bare line back as it was`() =
        runTest(dispatcher) {
            val viewModel = started()
            val bare = line("item-1", freeText = "written by hand", checked = true)
            items.emit(listOf(bare))
            advanceUntilIdle()

            viewModel.events.test {
                viewModel.moveCheckedToPantry(MoveToPantryOptions(defaultAmountForBare = true))
                advanceUntilIdle()
                viewModel.undoMove(checkNotNull(awaitMove().undo))
                advanceUntilIdle()
            }

            assertEquals(listOf(bare), items.upserts.last())
            assertTrue(pantry.held.isEmpty())
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
    fun `retry after a failed first load listens again and the lines arrive`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.errors.emit(AppError.Unauthorized())
            advanceUntilIdle()
            assertTrue(viewModel.state.value.canRetry)
            // The failed-list state carries the button; the banner above it does not repeat it.
            assertFalse(viewModel.state.value.bannerOffersRetry)

            viewModel.retry()
            advanceUntilIdle()

            // Loading again, on one item listener rather than two.
            assertTrue(viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.error)
            assertEquals(1, items.errors.subscriptionCount.value)

            items.emit(listOf(line("item-1")))
            advanceUntilIdle()
            assertFalse(viewModel.state.value.failedToLoad)
            assertEquals(1, viewModel.state.value.unchecked.size)
        }

    @Test
    fun `a refused write is not offered a retry`() =
        runTest(dispatcher) {
            val viewModel = started()
            items.emit(listOf(line("item-1")))
            advanceUntilIdle()
            items.upsertResult = AppResult.Failure(AppError.Network())

            viewModel.setChecked(itemId("item-1"), true)
            advanceUntilIdle()

            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error)
            assertFalse(viewModel.state.value.canRetry)
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
}
