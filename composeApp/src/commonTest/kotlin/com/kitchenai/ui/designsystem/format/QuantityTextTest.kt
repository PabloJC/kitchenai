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
}
