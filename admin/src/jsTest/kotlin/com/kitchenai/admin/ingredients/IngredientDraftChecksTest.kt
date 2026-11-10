package com.kitchenai.admin.ingredients

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IngredientDraftChecksTest {
    private val valid = IngredientDraft(id = "olive-oil", labels = listOf(LabelRow("en", "Olive oil")))

    @Test
    fun `a draft with an id and one name is accepted`() {
        assertNull(valid.problem())
    }

    @Test
    fun `a draft with no name gets the callable's labels message`() {
        assertEquals("labels needs 1 to 20 languages", valid.copy(labels = listOf(LabelRow("en", " "))).problem())
    }

    @Test
    fun `ids are refused with the callable's messages`() {
        assertEquals("id must be a short identifier", valid.copy(id = "").problem())
        assertEquals("id must not contain free text", valid.copy(id = "olive oil").problem())
        assertEquals("id is reserved", valid.copy(id = "__oil").problem())
    }

    @Test
    fun `a name needs a language tag and fits the callable's ceiling`() {
        assertEquals(
            "label key must be a language tag",
            valid.copy(labels = listOf(LabelRow("english", "Oil"))).problem(),
        )
        assertEquals(
            "label must be non-empty text of at most 200 characters",
            valid.copy(labels = listOf(LabelRow("en", "a".repeat(201)))).problem(),
        )
    }

    @Test
    fun `a language named twice is refused rather than collapsed`() {
        val twice = valid.copy(labels = listOf(LabelRow("en", "Oil"), LabelRow("en", "Olive oil")))

        assertEquals("each language can be named once", twice.problem())
    }
}
