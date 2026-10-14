package com.kitchenai.ui.designsystem.format

import kotlin.test.Test
import kotlin.test.assertEquals

class DecimalSeparatorTest {
    @Test
    fun `spanish from spain writes a comma`() {
        assertEquals(',', decimalSeparatorFor(listOf("es-ES", "en-US")))
    }

    @Test
    fun `english writes a dot`() {
        assertEquals('.', decimalSeparatorFor(listOf("en-GB")))
    }

    @Test
    fun `spanish from mexico writes a dot`() {
        assertEquals('.', decimalSeparatorFor(listOf("es-MX")))
    }

    @Test
    fun `a script subtag does not hide the region`() {
        assertEquals('.', decimalSeparatorFor(listOf("es-Latn-MX")))
    }

    @Test
    fun `only the first preferred locale counts`() {
        assertEquals('.', decimalSeparatorFor(listOf("en-US", "es-ES")))
    }

    @Test
    fun `a bare language and an underscore tag are both read`() {
        assertEquals(',', decimalSeparatorFor(listOf("fr")))
        assertEquals(',', decimalSeparatorFor(listOf("de_DE")))
    }

    @Test
    fun `no tags falls back to a dot`() {
        assertEquals('.', decimalSeparatorFor(emptyList()))
    }
}
