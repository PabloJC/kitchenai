package com.kitchenai.ui.presentation.suggestions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RecipeDetailSentenceTest {
    @Test
    fun `a skipped line is one nobody needs rather than one already on the list`() {
        val sentence = RecipeDetailEvent.AddedToList(added = 1, skipped = 5).sentence()

        // Those five were skipped because the pantry covers them or the recipe calls them
        // optional. Saying they were already listed sends the reader to check a list that
        // does not have them.
        assertEquals("1 added, 5 not needed", sentence)
        assertFalse(sentence.contains("already"), sentence)
    }

    @Test
    fun `a refusal is reported in its own words rather than wrapped`() {
        val sentence = RecipeDetailEvent.Failed("You are missing ingredients for this").sentence()

        assertEquals("You are missing ingredients for this", sentence)
    }
}
