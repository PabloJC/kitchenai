package com.kitchenai.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ConstraintStrengthTest {
    @Test
    fun `the cycle softens back round rather than ending on an exclusion`() {
        assertEquals(ConstraintStrength.AVOID, ConstraintStrength.PREFER.next())
        assertEquals(ConstraintStrength.EXCLUDE, ConstraintStrength.AVOID.next())
        assertEquals(ConstraintStrength.PREFER, ConstraintStrength.EXCLUDE.next())
    }

    @Test
    fun `every strength is reachable by repeating the choice`() {
        val walked = generateSequence(ConstraintStrength.SOFTEST) { it.next() }.take(3).toSet()

        assertEquals(ConstraintStrength.entries.toSet(), walked)
    }

    @Test
    fun `the first binding is the softest one`() {
        assertEquals(ConstraintStrength.PREFER, ConstraintStrength.SOFTEST)
    }
}
