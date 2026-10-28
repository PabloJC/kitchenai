package com.kitchenai.ui.presentation.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogueMatchTest {
    private val catalogue = listOf("Milk", "Limón", "Pan", "Empanada", "Whole  milk")

    @Test
    fun `a name typed in another case is the catalogue entry`() {
        assertEquals("Milk", match(" milk "))
    }

    @Test
    fun `accents and inner spacing are folded`() {
        assertEquals("Limón", match("limon"))
        assertEquals("Whole  milk", match("whole milk"))
    }

    @Test
    fun `a partial name is not a match`() {
        assertNull(match("pa"))
        assertEquals("Pan", match("PAN"))
        assertNull(match("empan"))
    }

    @Test
    fun `two entries with the same label are a tie and stay free text`() {
        assertNull(exactCatalogueMatch("salt", listOf("Salt", "salt")) { it })
    }

    @Test
    fun `blank text matches nothing`() {
        assertNull(match("  "))
    }

    private fun match(text: String): String? = exactCatalogueMatch(text, catalogue) { it }
}
