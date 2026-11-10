package com.kitchenai.admin.ingredients

import kotlin.test.Test
import kotlin.test.assertEquals

class IngredientQueryTest {
    private val catalogue =
        listOf(
            ingredient("rice", "en" to "Rice", "es" to "Arroz"),
            ingredient("egg", "en" to "Egg", tags = listOf(term("allergens", "egg"))),
            ingredient("lard", "en" to "Lard", archived = true),
        )

    @Test
    fun `by default only active ingredients are listed, by id`() {
        assertEquals(listOf("egg", "rice"), catalogue.matching(IngredientQuery()).ids())
    }

    @Test
    fun `the archived filter lists archived ones alone or with the rest`() {
        assertEquals(listOf("lard"), catalogue.matching(IngredientQuery(archived = ArchivedFilter.ARCHIVED)).ids())
        assertEquals(3, catalogue.matching(IngredientQuery(archived = ArchivedFilter.ALL)).size)
    }

    @Test
    fun `search matches the id or a name in any language, ignoring case`() {
        assertEquals(listOf("rice"), catalogue.matching(IngredientQuery(text = "ARRO")).ids())
        assertEquals(listOf("egg"), catalogue.matching(IngredientQuery(text = "eg")).ids())
    }

    @Test
    fun `a tag keeps the ingredients that carry it`() {
        assertEquals(listOf("egg"), catalogue.matching(IngredientQuery(tag = term("allergens", "egg"))).ids())
    }

    @Test
    fun `a term survives the trip through a select`() {
        assertEquals(term("allergens", "egg"), term("allergens", "egg").encoded().toTermRef())
        assertEquals(null, "no-slash".toTermRef())
    }

    private fun List<com.kitchenai.shared.domain.model.Ingredient>.ids() = map { it.id.value }
}
