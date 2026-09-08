package com.kitchenai.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ConstraintStrengthTest {
    @Test
    fun `the cycle is a two-way toggle rather than a one-way tightening`() {
        assertEquals(ConstraintStrength.AVOID, ConstraintStrength.PREFER.next())
        assertEquals(ConstraintStrength.PREFER, ConstraintStrength.AVOID.next())
    }

    @Test
    fun `every strength is reachable by repeating the choice`() {
        val walked = generateSequence(ConstraintStrength.SOFTEST) { it.next() }.take(2).toSet()

        assertEquals(ConstraintStrength.entries.toSet(), walked)
    }

    @Test
    fun `the first binding is the softest one`() {
        assertEquals(ConstraintStrength.PREFER, ConstraintStrength.SOFTEST)
    }
}
