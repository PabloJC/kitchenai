package com.kitchenai.ui.designsystem.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuantityTextTest {
    @Test
    fun `a dot is a decimal separator`() {
        assertEquals(1.5, parseAmount("1.5"))
    }

    @Test
    fun `a comma is a decimal separator too`() {
        // The device locale decides which key the keyboard offers, not the app.
        assertEquals(1.5, parseAmount("1,5"))
    }

    @Test
    fun `a whole number parses`() {
        assertEquals(2.0, parseAmount("2"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(0.5, parseAmount("  0,5  "))
    }

    @Test
    fun `empty input is not an amount`() {
        assertNull(parseAmount(""))
    }

    @Test
    fun `blank input is not an amount`() {
        assertNull(parseAmount("   "))
    }

    @Test
    fun `a negative is rejected`() {
        assertNull(parseAmount("-1.5"))
    }

    @Test
    fun `two separators are rejected`() {
        assertNull(parseAmount("1.5.5"))
    }

    @Test
    fun `a separator with no digits is rejected`() {
        assertNull(parseAmount(","))
    }

    @Test
    fun `letters are rejected`() {
        assertNull(parseAmount("1kg"))
    }

    @Test
    fun `scientific notation is rejected`() {
        assertNull(parseAmount("1e3"))
    }

    @Test
    fun `internal whitespace is rejected`() {
        assertNull(parseAmount("1 5"))
    }

    @Test
    fun `a whole amount keeps no fraction`() {
        assertEquals("2 pieces", formatQuantity(2.0, "pieces"))
    }

    @Test
    fun `a scaled amount is rounded to two decimals`() {
        // 100 g scaled to one of three servings.
        assertEquals("33.33 g", formatQuantity(100.0 / 3, "g"))
    }

    @Test
    fun `trailing zeros after rounding are dropped`() {
        assertEquals("1.5", formatAmount(1.499999))
        assertEquals("2", formatAmount(1.999))
    }

    @Test
    fun `a decimal comma replaces the dot`() {
        assertEquals("33,33 g", formatQuantity(100.0 / 3, "g", ','))
        assertEquals("1,5", formatAmount(1.5, ','))
    }

    @Test
    fun `a missing unit leaves the amount alone`() {
        assertEquals("0.25", formatQuantity(0.25, null))
    }

    @Test
    fun `an amount too small to show two decimals of rounds to zero`() {
        assertEquals("0", formatAmount(0.004))
    }

    @Test
    fun `what the field shows parses back to the same amount`() {
        assertEquals(1.5, parseAmount(formatAmount(1.5, ',')))
    }
}
