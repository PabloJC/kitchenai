package com.kitchenai.ui.presentation.suggestions.cooking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimerClockTest {
    @Test
    fun `minutes and seconds under an hour`() {
        assertEquals("10:00", 10.minutes.toClock())
        assertEquals("9:05", (9.minutes + 5.seconds).toClock())
        assertEquals("0:00", Duration.ZERO.toClock())
    }

    @Test
    fun `hours once there are any`() {
        assertEquals("1:02:05", (1.hours + 2.minutes + 5.seconds).toClock())
    }

    @Test
    fun `a part second still running reads as a whole one`() {
        assertEquals("0:01", 200.milliseconds.toClock())
    }
}
