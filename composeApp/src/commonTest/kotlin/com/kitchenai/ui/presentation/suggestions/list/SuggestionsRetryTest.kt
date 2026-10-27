package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsRetryTest : SuggestionsViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `try again after a failed run asks for ideas again`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Failure(AppError.Network())
            val viewModel = started()
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.error)

            agent.answer = AppResult.Success(listOf(suggestion("recipe-1")))
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(2, agent.calls)
            assertNull(viewModel.state.value.error)
            assertEquals(1, viewModel.state.value.suggestions.size)
        }

    @Test
    fun `try again after the kitchen never answered listens for it again and then runs`() =
        runTest(dispatcher) {
            kitchens.readError = AppError.Network()
            val viewModel = started()
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.error)
            assertEquals(0, agent.calls)

            kitchens.readError = null
            viewModel.retry()
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
            assertEquals(1, agent.calls)
        }

    @Test
    fun `offline the arrival run and the button wait and both come back with the connection`() =
        runTest(dispatcher) {
            network.setOnline(false)
            val viewModel = started()
            advanceUntilIdle()

            viewModel.generate()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.isOnline)
            assertEquals(0, agent.calls)

            network.setOnline(true)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isOnline)
            viewModel.generate()
            advanceUntilIdle()
            assertEquals(1, agent.calls)
        }
}
