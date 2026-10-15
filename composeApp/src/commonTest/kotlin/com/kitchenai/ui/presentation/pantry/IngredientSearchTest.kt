package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IngredientSearchTest {
    @Test
    fun `an unaccented query finds an accented label`() {
        val found = options("Limón", "Lima").rankedMatches("limon")

        assertEquals(listOf("Limón"), found.map { it.label })
    }

    @Test
    fun `an accented query finds an unaccented label`() {
        assertEquals(listOf("Pina"), options("Pina").rankedMatches("piña").map { it.label })
    }

    @Test
    fun `a decomposed accent folds like a precomposed one`() {
        assertEquals("limon", foldForSearch("Lim" + "ó" + "n"))
    }

    @Test
    fun `case is ignored`() {
        assertEquals(listOf("Tomato"), options("Tomato").rankedMatches("TOM").map { it.label })
    }

    @Test
    fun `an empty query finds nothing rather than the whole catalogue`() {
        assertTrue(options("Tomato", "Salt").rankedMatches("").isEmpty())
        assertTrue(options("Tomato", "Salt").rankedMatches("   ").isEmpty())
    }

    @Test
    fun `prefix matches rank before word starts and word starts before contains`() {
        val found = options("Empanada", "Whole pan", "Pan").rankedMatches("pan")

        assertEquals(listOf("Pan", "Whole pan", "Empanada"), found.map { it.label })
    }

    @Test
    fun `within a tier the shorter name comes first`() {
        val found = options("Pan integral de centeno", "Pan", "Pan blanco").rankedMatches("pan")

        assertEquals(listOf("Pan", "Pan blanco", "Pan integral de centeno"), found.map { it.label })
    }

    @Test
    fun `a label that does not contain the query is left out`() {
        assertTrue(options("Salt").rankedMatches("tom").isEmpty())
    }

    @Test
    fun `at most the limit is returned`() {
        val many = options(*Array(20) { index -> "Pan $index" })

        assertEquals(MAX_INGREDIENT_MATCHES, many.rankedMatches("pan").size)
    }

    private fun options(vararg labels: String): List<IngredientOption> =
        labels.mapIndexed { index, label ->
            IngredientOption((IngredientId.of("ingredient-$index") as AppResult.Success).data, label, null)
        }
}
