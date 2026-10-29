package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.ui.presentation.common.FakeRecipePort
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The heart on an idea card, and the earlier generations New ideas no longer throws away (#349). */
@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsHistoryTest : SuggestionsViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the heart on an idea saves it and a second tap unsaves it with an undo`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started(recipes = recipes)
            advanceUntilIdle()
            val seen = mutableListOf<SuggestionsEvent>()
            val collector = launch { viewModel.events.toList(seen) }
            val id = RecipeId.of("recipe-1").orFail()

            viewModel.toggleSaved(id)
            advanceUntilIdle()

            assertTrue(id in viewModel.state.value.savedIds)
            assertEquals(listOf("recipe-1"), recipes.saved.map { it.id.value })

            viewModel.toggleSaved(id)
            advanceUntilIdle()
            collector.cancel()

            assertTrue(id !in viewModel.state.value.savedIds)
            assertEquals(SuggestionsEvent.Saved, seen.first())
            assertEquals("recipe-1", (seen.last() as SuggestionsEvent.Unsaved).restore.id.value)
        }

    @Test
    fun `new ideas keep the set before them as an earlier generation`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("first-1"), suggestion("first-2")))
            val viewModel = started()
            advanceUntilIdle()

            agent.answer = AppResult.Success(listOf(suggestion("second-1")))
            viewModel.generate()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf("second-1"), state.shownSuggestions.map { it.id.value })
            assertEquals(listOf(listOf("first-1", "first-2")), state.earlier.map { set -> set.map { it.id.value } })
        }

    @Test
    fun `browsing an earlier set shows its cards and its actions still find the recipe`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort()
            agent.answer = AppResult.Success(listOf(suggestion("first-1")))
            val viewModel = started(recipes = recipes)
            advanceUntilIdle()
            agent.answer = AppResult.Success(listOf(suggestion("second-1")))
            viewModel.generate()
            advanceUntilIdle()

            viewModel.browseEarlier(0)
            assertEquals(listOf("first-1"), viewModel.state.value.shownSuggestions.map { it.id.value })
            viewModel.toggleSaved(RecipeId.of("first-1").orFail())
            advanceUntilIdle()
            assertEquals(listOf("first-1"), recipes.saved.map { it.id.value })

            viewModel.browseEarlier(null)
            assertEquals(listOf("second-1"), viewModel.state.value.shownSuggestions.map { it.id.value })
        }

    @Test
    fun `only the kept generations stay reachable and a new run returns to the latest`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("run-1")))
            val viewModel = started()
            advanceUntilIdle()
            (2..4).forEach { n ->
                viewModel.browseEarlier(0)
                agent.answer = AppResult.Success(listOf(suggestion("run-$n")))
                viewModel.generate()
                advanceUntilIdle()
            }

            val state = viewModel.state.value
            assertNull(state.browsingEarlier)
            assertEquals(listOf("run-4"), state.shownSuggestions.map { it.id.value })
            assertEquals(listOf("run-3", "run-2"), state.earlier.map { set -> set.single().id.value })
        }

    @Test
    fun `a launch brings back the stored generations and a run that finds nothing keeps them reachable`() =
        runTest(dispatcher) {
            val recipes = FakeRecipePort(stored = listOf(dish("old-1")))
            recipes.addGeneration(listOf(dish("new-1")), keep = 3)
            agent.gate = CompletableDeferred()
            agent.answer = AppResult.Success(emptyList())

            val viewModel = started(recipes = recipes)
            dispatcher.scheduler.runCurrent()

            assertEquals(listOf("new-1"), viewModel.state.value.suggestions.map { it.id.value })
            assertEquals(listOf(listOf("old-1")), viewModel.state.value.earlier.map { set -> set.map { it.id.value } })

            agent.gate?.complete(Unit)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.suggestions.isEmpty())
            assertEquals(
                listOf(listOf("new-1"), listOf("old-1")),
                viewModel.state.value.earlier.map { set -> set.map { it.id.value } },
            )
        }

    @Test
    fun `browsing past the last earlier set is ignored`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("only-1")))
            val viewModel = started()
            advanceUntilIdle()

            viewModel.browseEarlier(0)

            assertNull(viewModel.state.value.browsingEarlier)
        }
}
