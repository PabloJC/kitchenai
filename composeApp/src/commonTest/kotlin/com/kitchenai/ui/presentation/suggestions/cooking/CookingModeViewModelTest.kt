package com.kitchenai.ui.presentation.suggestions.cooking

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.port.TimeProvider
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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CookingModeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val timers = RunningTimers()
    private val alarms = RecordingCookingTimerScheduler()

    // Wall-clock time that moves with the test scheduler, as the real one moves with the world.
    private val time = TimeProvider { Instant.fromEpochMilliseconds(dispatcher.scheduler.currentTime) }

    private fun opened(recipe: RecipeId = pasta): CookingModeViewModel =
        CookingModeViewModel(timers, alarms, time).also { it.open(recipe) }

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
        val viewModel = opened()

        viewModel.toggleIngredient(2)
        viewModel.toggleIngredient(0)
        assertEquals(setOf(0, 2), viewModel.state.value.ticked)

        viewModel.toggleIngredient(2)
        assertEquals(setOf(0), viewModel.state.value.ticked)
    }

    @Test
    fun `a timer counts down, hands its alarm to the system and says so once at zero`() =
        runTest(dispatcher) {
            val viewModel = opened()
            val seen = mutableListOf<TimerDone>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.startTimer(step = 2, duration = 3.seconds, text)
            runCurrent()
            assertEquals(Instant.fromEpochMilliseconds(3_000), alarms.pending.values.single().fireAt)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(listOf(StepTimerUi(2, 2.seconds)), viewModel.state.value.timers)
            assertTrue(seen.isEmpty())

            advanceUntilIdle()
            collector.cancel()
            assertEquals(listOf(StepTimerUi(2, Duration.ZERO)), viewModel.state.value.timers)
            assertEquals(listOf(TimerDone(2)), seen)
        }

    @Test
    fun `two steps run together and each ends on its own`() =
        runTest(dispatcher) {
            val viewModel = opened()
            val seen = mutableListOf<TimerDone>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.startTimer(step = 1, duration = 10.seconds, text)
            advanceTimeBy(5.seconds)
            viewModel.startTimer(step = 3, duration = 2.seconds, text)
            advanceUntilIdle()
            collector.cancel()

            assertEquals(listOf(TimerDone(3), TimerDone(1)), seen)
            assertEquals(2, alarms.pending.size)
        }

    @Test
    fun `restarting a step replaces only that step's timer and alarm`() =
        runTest(dispatcher) {
            val viewModel = opened()

            viewModel.startTimer(step = 1, duration = 10.seconds, text)
            viewModel.startTimer(step = 2, duration = 10.seconds, text)
            advanceTimeBy(4.seconds)
            viewModel.startTimer(step = 1, duration = 10.seconds, text)
            runCurrent()

            assertEquals(listOf(StepTimerUi(1, 10.seconds), StepTimerUi(2, 6.seconds)), viewModel.state.value.timers)
            assertEquals(2, alarms.pending.size)
        }

    @Test
    fun `dismissing a running timer stops it and its alarm without an alert`() =
        runTest(dispatcher) {
            val viewModel = opened()
            val seen = mutableListOf<TimerDone>()
            val collector = launch { viewModel.events.toList(seen) }

            viewModel.startTimer(step = 1, duration = 10.seconds, text)
            advanceTimeBy(5.seconds)
            viewModel.dismissTimer(1)
            advanceUntilIdle()
            collector.cancel()

            assertTrue(viewModel.state.value.timers.isEmpty())
            assertTrue(alarms.pending.isEmpty())
            assertTrue(seen.isEmpty())
        }

    @Test
    fun `leaving cooking mode keeps the timer, and coming back shows it without a second alert`() =
        runTest(dispatcher) {
            val first = opened()
            first.startTimer(step = 1, duration = 3.seconds, text)
            runCurrent()
            advanceTimeBy(10.seconds)

            val second = opened()
            val seen = mutableListOf<TimerDone>()
            val collector = launch { second.events.toList(seen) }
            advanceUntilIdle()
            collector.cancel()

            assertEquals(listOf(StepTimerUi(1, Duration.ZERO)), second.state.value.timers)
            assertTrue(seen.isEmpty())
            assertEquals(1, timers.of(pasta).size)
        }

    @Test
    fun `another recipe's timers are not this recipe's`() =
        runTest(dispatcher) {
            opened(soup).startTimer(step = 1, duration = 10.seconds, text)

            val viewModel = opened()
            runCurrent()

            assertTrue(viewModel.state.value.timers.isEmpty())
        }

    @Test
    fun `a zero-length timer is not started`() {
        val viewModel = opened()

        viewModel.startTimer(step = 1, duration = Duration.ZERO, text)

        assertTrue(timers.running.value.isEmpty())
        assertTrue(alarms.pending.isEmpty())
    }
}

private val pasta = (RecipeId.of("recipe-1") as AppResult.Success).data
private val soup = (RecipeId.of("recipe-2") as AppResult.Success).data
private val text = CookingAlarmText("title", "body", "channel", "stop")
