package com.kitchenai.ui.designsystem.format

import kotlin.test.Test
import kotlin.test.assertEquals

class CoverageTextTest {
    @Test
    fun `a zero total is no coverage rather than a division by zero`() {
        assertEquals(0f, coverageFraction(covered = 0, total = 0))
    }

    @Test
    fun `a negative total is no coverage either`() {
        assertEquals(0f, coverageFraction(covered = 3, total = -1))
    }

    @Test
    fun `partial coverage is the ratio`() {
        assertEquals(0.5f, coverageFraction(covered = 2, total = 4))
    }

    @Test
    fun `full coverage is one`() {
        assertEquals(1f, coverageFraction(covered = 4, total = 4))
    }

    @Test
    fun `more covered than total clamps to one`() {
        assertEquals(1f, coverageFraction(covered = 7, total = 4))
    }

    @Test
    fun `a negative covered clamps to zero`() {
        assertEquals(0f, coverageFraction(covered = -2, total = 4))
    }
}
