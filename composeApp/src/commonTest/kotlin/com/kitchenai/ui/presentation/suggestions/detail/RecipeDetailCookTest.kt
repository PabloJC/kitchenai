package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.FakeRecipePort
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.defaultKitchenId
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_missing_ingredients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailCookTest : RecipeDetailViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the review lists each holding a cook would change and writes nothing`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(500.0)))
            val viewModel = started(pantry = listOf(holding(500.0)), pantryPort = pantry)
            advanceUntilIdle()

            viewModel.reviewCook()
            advanceUntilIdle()

            val review = viewModel.state.value.cookReview!!
            assertEquals(listOf("rice"), review.deductions.map { it.name })
            assertNotNull(review.deductions.single().left)
            assertTrue(review.missing.isEmpty())
            assertEquals(500.0, pantry.held.single().quantity.amount)

            viewModel.dismissCookReview()
            assertNull(viewModel.state.value.cookReview)
        }

    @Test
    fun `a holding the cook uses up reads as used up rather than as zero left`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = listOf(holding(200.0)))
            advanceUntilIdle()

            viewModel.reviewCook()
            advanceUntilIdle()

            assertNull(viewModel.state.value.cookReview!!.deductions.single().left)
        }

    @Test
    fun `with something missing the review names it and the cook goes ahead with what is held`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(emptyList())
            val viewModel = started(pantry = emptyList(), pantryPort = pantry)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.canCook)
            assertTrue(viewModel.state.value.cooksAnyway)
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.reviewCook()
            advanceUntilIdle()
            assertEquals(listOf("rice"), viewModel.state.value.cookReview!!.missing)
            viewModel.cook()
            advanceUntilIdle()
            collector.cancel()

            val cooked = seen.filterIsInstance<RecipeDetailEvent.Cooked>().single()
            assertTrue(cooked.cooked.deductions.isEmpty())
            assertNull(viewModel.state.value.cookReview)
        }

    @Test
    fun `a holding lost after a full review is refused rather than cooked anyway`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(200.0)))
            val viewModel = started(pantry = listOf(holding(200.0)), pantryPort = pantry)
            advanceUntilIdle()
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            // The screen said nothing was missing; the holding goes before the write runs.
            viewModel.cook()
            pantry.remove(defaultKitchenId, holding(200.0).id)
            advanceUntilIdle()
            collector.cancel()

            val failure = seen.filterIsInstance<RecipeDetailEvent.Failed>().single()
            assertEquals(UiText.of(Res.string.error_missing_ingredients), failure.message)
        }

    @Test
    fun `undoing a cook gives the pantry back what it took`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(500.0)))
            val viewModel = started(pantry = listOf(holding(500.0)), pantryPort = pantry)
            advanceUntilIdle()
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.cook()
            advanceUntilIdle()
            assertEquals(300.0, pantry.held.single().quantity.amount)
            viewModel.undoCook(seen.filterIsInstance<RecipeDetailEvent.Cooked>().single().cooked)
            advanceUntilIdle()
            collector.cancel()

            assertEquals(500.0, pantry.held.single().quantity.amount)
            assertEquals(RecipeDetailEvent.CookUndone, seen.last())
        }

    @Test
    fun `a cook that empties a row offers it back to the list as a bare line`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(200.0)))
            val viewModel = started(pantry = listOf(holding(200.0)), pantryPort = pantry)
            advanceUntilIdle()
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.cook()
            advanceUntilIdle()
            val cooked = seen.filterIsInstance<RecipeDetailEvent.Cooked>().single().cooked
            assertEquals(listOf(holding(200.0)), cooked.usedUp)
            viewModel.addUsedUpToList(cooked)
            advanceUntilIdle()
            collector.cancel()

            assertEquals(RecipeDetailEvent.UsedUpAddedToList(1), seen.last())
            val line = items.upserts.last().single()
            assertEquals(holding(200.0).ingredient, line.ingredient)
            assertNull(line.quantity)
        }

    @Test
    fun `cooking takes the ingredients out of the pantry`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(holding(500.0)))
            val viewModel = started(pantry = listOf(holding(500.0)), pantryPort = pantry)
            advanceUntilIdle()

            viewModel.cook()
            advanceUntilIdle()

            assertEquals(300.0, pantry.held.single().quantity.amount)
        }

    @Test
    fun `a cook that worked is never followed by a failure`() =
        runTest(dispatcher) {
            // Enough for one cook and not for the next, so the buckets have to move afterwards.
            val recipes = FakeRecipePort(catalogue = emptyList(), stored = listOf(dish))
            val pantry = FakePantryPort(listOf(holding(350.0)))
            val viewModel = started(pantry = listOf(holding(350.0)), pantryPort = pantry, recipePort = recipes)
            advanceUntilIdle()
            val seen = mutableListOf<RecipeDetailEvent>()
            val collector = launch { viewModel.events.toList(seen) }

            // A new generation replaces the stored one while this screen is still open, so the
            // dish this ViewModel is showing exists nowhere any longer.
            recipes.replaceAll(emptyList())
            viewModel.cook()
            advanceUntilIdle()
            collector.cancel()

            assertTrue(seen.single() is RecipeDetailEvent.Cooked)
            assertEquals(150.0, pantry.held.single().quantity.amount)
            // And the buckets still refreshed: what is left no longer covers another serving.
            assertEquals(listOf("rice"), viewModel.state.value.missing.map { it.name })
        }
}
