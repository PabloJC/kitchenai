package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppResult
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.defaultKitchenId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsEmptyPantryTest : SuggestionsViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an empty pantry spends no model call on arrival and says so`() =
        runTest(dispatcher) {
            val viewModel = started(pantryPort = FakePantryPort())
            advanceUntilIdle()

            assertEquals(0, agent.calls)
            assertTrue(viewModel.state.value.isPantryEmpty)
            assertFalse(viewModel.state.value.hasGenerated)
        }

    @Test
    fun `the button still runs one on an empty pantry when asked`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            val viewModel = started(pantryPort = FakePantryPort())
            advanceUntilIdle()

            viewModel.generate()
            advanceUntilIdle()

            assertEquals(1, agent.calls)
            assertEquals(listOf("recipe-1"), viewModel.state.value.suggestions.map { it.id.value })
        }

    @Test
    fun `filling the pantry clears the empty flag`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort()
            val viewModel = started(pantryPort = pantry)
            advanceUntilIdle()

            pantry.upsert(defaultKitchenId, riceHolding())
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isPantryEmpty)
        }

    @Test
    fun `a held pantry generates on arrival as before`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            assertEquals(1, agent.calls)
            assertFalse(viewModel.state.value.isPantryEmpty)
        }
}
