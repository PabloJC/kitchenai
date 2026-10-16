package com.kitchenai.ui.presentation.suggestions.cooking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CookingModeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an ingredient ticks and unticks`() {
        val viewModel = CookingModeViewModel()

        viewModel.toggleIngredient(2)
        viewModel.toggleIngredient(0)
        assertEquals(setOf(0, 2), viewModel.state.value.ticked)

        viewModel.toggleIngredient(2)
        assertEquals(setOf(0), viewModel.state.value.ticked)
    }

    @Test
    fun `a timer counts down and says so once when it reaches zero`() =
        runTest(dispatcher) {
            val viewModel = CookingModeViewModel(dispatcher.scheduler.timeSource)
            val seen = mutableListOf<TimerDone>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.startTimer(step = 2, duration = 3.seconds)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(StepTimerUi(2, 2.seconds), viewModel.state.value.timer)
            assertTrue(seen.isEmpty())

            advanceUntilIdle()
            collector.cancel()
            assertEquals(StepTimerUi(2, Duration.ZERO), viewModel.state.value.timer)
            assertTrue(viewModel.state.value.timer!!.isDone)
            assertEquals(listOf(TimerDone(2)), seen)
        }

    @Test
    fun `a second timer replaces the first, which never goes off`() =
        runTest(dispatcher) {
            val viewModel = CookingModeViewModel(dispatcher.scheduler.timeSource)
            val seen = mutableListOf<TimerDone>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.startTimer(step = 1, duration = 10.seconds)
            advanceTimeBy(5.seconds)
            viewModel.startTimer(step = 3, duration = 2.seconds)
            advanceUntilIdle()
            collector.cancel()

            assertEquals(listOf(TimerDone(3)), seen)
        }

    @Test
    fun `dismissing a running timer stops it without an alert`() =
        runTest(dispatcher) {
            val viewModel = CookingModeViewModel(dispatcher.scheduler.timeSource)
            val seen = mutableListOf<TimerDone>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.startTimer(step = 1, duration = 10.seconds)
            advanceTimeBy(5.seconds)
            viewModel.dismissTimer()
            advanceUntilIdle()
            collector.cancel()

            assertNull(viewModel.state.value.timer)
            assertTrue(seen.isEmpty())
        }

    @Test
    fun `a zero-length timer is not started`() {
        val viewModel = CookingModeViewModel()

        viewModel.startTimer(step = 1, duration = Duration.ZERO)

        assertNull(viewModel.state.value.timer)
    }
}
