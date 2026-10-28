package com.kitchenai.ui.presentation.suggestions.cooking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.ui.platform.CookingAlarm
import com.kitchenai.ui.platform.CookingTimerScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The ticked ingredients and this recipe's step timers. The timers themselves live in [timers], so
 * leaving the screen keeps them, and in [alarms], which is what rings with the phone locked.
 */
class CookingModeViewModel(
    private val timers: RunningTimers,
    private val alarms: CookingTimerScheduler,
    private val time: TimeProvider,
) : ViewModel() {
    private val internalState = MutableStateFlow(CookingModeUiState())
    val state: StateFlow<CookingModeUiState> = internalState.asStateFlow()

    private val doneChannel = Channel<TimerDone>(Channel.BUFFERED)
    val events: Flow<TimerDone> = doneChannel.receiveAsFlow()

    private var recipeId: RecipeId? = null
    private var ticker: Job? = null

    // Steps already announced, so a timer that ended while the screen was away does not buzz again.
    private val announced = mutableSetOf<Int>()

    fun open(recipe: RecipeId) {
        if (recipeId == recipe) return
        recipeId = recipe
        announced.clear()
        announced += timers.of(recipe).filter { it.endsAt <= time.now() }.map { it.step }
        ticker?.cancel()
        ticker = viewModelScope.launch { timers.running.collectLatest { tick(recipe) } }
    }

    fun toggleIngredient(index: Int) =
        internalState.update { state ->
            state.copy(ticked = if (index in state.ticked) state.ticked - index else state.ticked + index)
        }

    /** Starting a step's timer again restarts only that step's; the others keep running. */
    fun startTimer(
        step: Int,
        duration: Duration,
        text: CookingAlarmText,
    ) {
        val recipe = recipeId ?: return
        if (duration <= Duration.ZERO) return
        val timer = RunningTimer(recipe, step, time.now() + duration)
        announced -= step
        alarms.schedule(
            CookingAlarm(timer.alarmId, timer.endsAt, text.title, text.body, text.channelName, text.stopLabel),
        )
        timers.put(timer)
    }

    /** Stops a running timer, or clears one that has finished, and its notification with it. */
    fun dismissTimer(step: Int) {
        val recipe = recipeId ?: return
        timers.of(recipe).firstOrNull { it.step == step }?.let { alarms.cancel(it.alarmId) }
        timers.remove(recipe, step)
        announced -= step
    }

    // Re-reads the clock on every tick, so a slow tick never adds up to drift.
    private suspend fun tick(recipe: RecipeId) {
        val mine = timers.of(recipe).sortedBy { it.step }
        while (true) {
            val now = time.now()
            val shown = mine.map { StepTimerUi(it.step, (it.endsAt - now).coerceAtLeast(Duration.ZERO)) }
            internalState.update { it.copy(timers = shown) }
            shown.filter { it.isDone && announced.add(it.step) }.forEach { doneChannel.send(TimerDone(it.step)) }
            val next = shown.filterNot { it.isDone }.minOfOrNull { it.remaining } ?: return
            delay(minOf(TICK, next))
        }
    }

    private companion object {
        val TICK = 1.seconds
    }
}
