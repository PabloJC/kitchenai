package com.kitchenai.ui.presentation.suggestions.cooking

import kotlin.time.Duration

/** The one running timer, and the step it was started from; [step] counts from one, as on screen. */
data class StepTimerUi(
    val step: Int,
    val remaining: Duration,
) {
    val isDone: Boolean get() = remaining <= Duration.ZERO
}
