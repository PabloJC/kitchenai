package com.kitchenai.shared.domain.service

import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.freeTextPantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.recipe.recipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PantryCandidateMatcherTest {
    @Test
    fun `a free-text holding whose words contain the line's is offered as a candidate`() {
        val line = recipeIngredient(freeText = "bread")
        val holding = freeTextPantryItem("item-1", "the good bread", Quantity(1.0))

        val candidates = PantryCandidateMatcher.candidatesFor(listOf(line), listOf(holding))

        assertEquals(listOf(holding), candidates[line])
    }

    @Test
    fun `a free-text line whose words contain the holding's is offered as a candidate`() {
        val line = recipeIngredient(freeText = "a pinch of salt")
        val holding = freeTextPantryItem("item-1", "salt", Quantity(1.0))

        val candidates = PantryCandidateMatcher.candidatesFor(listOf(line), listOf(holding))

        assertEquals(listOf(holding), candidates[line])
    }

    @Test
    fun `matching is case-insensitive and ignores surrounding whitespace`() {
        val line = recipeIngredient(freeText = "  BREAD  ")
        val holding = freeTextPantryItem("item-1", "bread", Quantity(1.0))

        val candidates = PantryCandidateMatcher.candidatesFor(listOf(line), listOf(holding))

        assertEquals(listOf(holding), candidates[line])
    }

    @Test
    fun `an unrelated holding is not offered`() {
        val line = recipeIngredient(freeText = "bread")
        val holding = freeTextPantryItem("item-1", "olive oil", Quantity(1.0))

        val candidates = PantryCandidateMatcher.candidatesFor(listOf(line), listOf(holding))

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `a catalogue holding is never offered even when it happens to share a line's id text`() {
        val line = recipeIngredient(freeText = "rice")
        val holding = pantryItem("item-1", "rice", Quantity(1.0))

        val candidates = PantryCandidateMatcher.candidatesFor(listOf(line), listOf(holding))

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `an unverifiable line that already names a catalogue ingredient is never a candidate line`() {
        // Unverifiable only because of a unit mismatch: it already has an ingredient, so a
        // free-text holding's own words have nothing to add.
        val line = recipeIngredient("rice", quantity = Quantity(1.0))
        val holding = freeTextPantryItem("item-1", "rice", Quantity(1.0))

        val candidates = PantryCandidateMatcher.candidatesFor(listOf(line), listOf(holding))

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `no free-text holdings at all means no candidates for anyone`() {
        val line = recipeIngredient(freeText = "bread")

        val candidates = PantryCandidateMatcher.candidatesFor(listOf(line), emptyList())

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `two plausible holdings are both offered for the same line`() {
        val line = recipeIngredient(freeText = "bread")
        val sourdough = freeTextPantryItem("item-1", "sourdough bread", Quantity(1.0))
        val rye = freeTextPantryItem("item-2", "rye bread", Quantity(1.0))

        val candidates = PantryCandidateMatcher.candidatesFor(listOf(line), listOf(sourdough, rye))

        assertEquals(listOf(sourdough, rye), candidates[line])
    }
}
