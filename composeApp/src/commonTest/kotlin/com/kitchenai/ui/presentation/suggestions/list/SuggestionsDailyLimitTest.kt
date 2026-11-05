package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.MealType
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.suggestions_daily_limit_any
import com.kitchenai.ui.resources.suggestions_daily_limit_dinner
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsDailyLimitTest : SuggestionsViewModelHarness() {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a meal already served today keeps the set on screen and says so without an error`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Success(listOf(suggestion("today-1")))
            val viewModel = started()
            advanceUntilIdle()

            agent.answer = AppResult.Failure(AppError.DailyLimit)
            viewModel.generate()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf("today-1"), state.suggestions.map { it.id.value })
            assertNull(state.error)
            assertFalse(state.isGenerating)
            assertEquals(UiText.of(Res.string.suggestions_daily_limit_any), state.dailyLimit)
        }

    @Test
    fun `the note names the meal that was asked for`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            agent.answer = AppResult.Failure(AppError.DailyLimit)
            viewModel.setMealType(MealType.DINNER)
            viewModel.generate()
            advanceUntilIdle()

            assertEquals(UiText.of(Res.string.suggestions_daily_limit_dinner), viewModel.state.value.dailyLimit)
        }

    @Test
    fun `the run on arrival keeps a served meal quietly`() =
        runTest(dispatcher) {
            agent.answer = AppResult.Failure(AppError.DailyLimit)
            val viewModel = started()
            advanceUntilIdle()

            assertNull(viewModel.state.value.dailyLimit)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `choosing another meal drops the note`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()
            agent.answer = AppResult.Failure(AppError.DailyLimit)
            viewModel.generate()
            advanceUntilIdle()

            viewModel.setMealType(MealType.LUNCH)

            assertNull(viewModel.state.value.dailyLimit)
        }

    @Test
    fun `a new run that lands drops the note`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()
            agent.answer = AppResult.Failure(AppError.DailyLimit)
            viewModel.generate()
            advanceUntilIdle()

            agent.answer = AppResult.Success(listOf(suggestion("lunch-1")))
            viewModel.setMealType(MealType.LUNCH)
            viewModel.generate()
            advanceUntilIdle()

            assertNull(viewModel.state.value.dailyLimit)
            assertEquals(listOf("lunch-1"), viewModel.state.value.suggestions.map { it.id.value })
        }
}
