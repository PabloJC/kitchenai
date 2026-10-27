package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.domain.model.MealType
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsFiltersTest : SuggestionsViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a meal and the expiring-first chip offer an update and never call the model by themselves`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()
            val arrival = agent.calls

            viewModel.setMealType(MealType.DINNER)
            viewModel.setUseExpiringSoon(true)
            advanceUntilIdle()

            assertEquals(arrival, agent.calls)
            assertTrue(viewModel.state.value.optionsChanged)
        }

    @Test
    fun `the chosen meal and the expiring-first flag reach the request`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            viewModel.setMealType(MealType.BREAKFAST)
            viewModel.setUseExpiringSoon(true)
            viewModel.generate()
            advanceUntilIdle()

            val sent = requireNotNull(agent.lastOptions)
            assertEquals(MealType.BREAKFAST, sent.mealType)
            assertTrue(sent.useExpiringSoon)
        }

    @Test
    fun `tapping the chosen meal again means any meal`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            viewModel.setMealType(MealType.LUNCH)
            viewModel.setMealType(null)

            assertNull(viewModel.state.value.options.mealType)
        }
}
