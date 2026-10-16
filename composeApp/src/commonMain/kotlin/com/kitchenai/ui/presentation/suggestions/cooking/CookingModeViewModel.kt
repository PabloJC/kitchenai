package com.kitchenai.ui.presentation.suggestions.cooking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The ticked ingredients and the step timer. The timer lives here rather than in the screen so a
 * rotation does not reset it, and reads [clock] on every tick so a slow tick never adds up to drift.
 */
class CookingModeViewModel(
    private val clock: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : ViewModel() {
    private val internalState = MutableStateFlow(CookingModeUiState())
    val state: StateFlow<CookingModeUiState> = internalState.asStateFlow()

    private val doneChannel = Channel<TimerDone>(Channel.BUFFERED)
    val events: Flow<TimerDone> = doneChannel.receiveAsFlow()

    private var countdown: Job? = null

    fun toggleIngredient(index: Int) =
        internalState.update { state ->
            state.copy(ticked = if (index in state.ticked) state.ticked - index else state.ticked + index)
        }

    /** One timer at a time: a second one replaces the first, which a pager of steps keeps legible. */
    fun startTimer(
        step: Int,
        duration: Duration,
    ) {
        if (duration <= Duration.ZERO) return
        countdown?.cancel()
        val end = clock.markNow() + duration
        internalState.update { it.copy(timer = StepTimerUi(step, duration)) }
        countdown =
            viewModelScope.launch {
                while (true) {
                    val left = (-end.elapsedNow()).coerceAtLeast(Duration.ZERO)
                    internalState.update { it.copy(timer = StepTimerUi(step, left)) }
                    if (left == Duration.ZERO) break
                    delay(minOf(TICK, left))
                }
                doneChannel.send(TimerDone(step))
            }
    }

    /** Stops a running timer, or clears one that has finished. */
    fun dismissTimer() {
        countdown?.cancel()
        countdown = null
        internalState.update { it.copy(timer = null) }
    }

    private companion object {
        val TICK = 1.seconds
    }
}
