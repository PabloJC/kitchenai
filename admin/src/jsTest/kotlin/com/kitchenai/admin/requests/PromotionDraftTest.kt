package com.kitchenai.admin.requests

import com.kitchenai.admin.ingredients.LabelRow
import com.kitchenai.admin.ingredients.problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromotionDraftTest {
    @Test
    fun `the slug drops accents, case and anything outside the id alphabet`() {
        assertEquals("tomate-cherry", slugFor("  Tomate   Cherry "))
        assertEquals("jalapeno", slugFor("Jalapeño"))
        assertEquals("salsa-de-soja-baja-en-sal", slugFor("Salsa de soja (baja en sal)!"))
        assertEquals("", slugFor("¡¿?!"))
    }

    @Test
    fun `the draft is new, named in the typed language, and valid as it comes`() {
        val draft = request("a", "Tomate cherry").toIngredientDraft("es")

        assertTrue(draft.isNew)
        assertEquals("tomate-cherry", draft.id)
        assertEquals(listOf(LabelRow("es", "Tomate cherry"), LabelRow("en", "")), draft.labels)
        assertNull(draft.problem())
    }
}
