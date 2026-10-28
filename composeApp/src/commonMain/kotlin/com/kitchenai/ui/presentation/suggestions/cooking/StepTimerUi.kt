package com.kitchenai.ui.presentation.suggestions.cooking

import kotlin.time.Duration

/** A step's timer as drawn; [step] counts from one, as on screen. */
data class StepTimerUi(
    val step: Int,
    val remaining: Duration,
) {
    val isDone: Boolean get() = remaining <= Duration.ZERO
}
