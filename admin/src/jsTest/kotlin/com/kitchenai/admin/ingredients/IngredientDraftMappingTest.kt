package com.kitchenai.admin.ingredients

import com.kitchenai.shared.data.remote.dto.IngredientDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IngredientDraftMappingTest {
    @Test
    fun `the payload is the whole document with blank names left out and tags grouped`() {
        val draft =
            IngredientDraft(
                id = "egg",
                labels = listOf(LabelRow("en", "Egg"), LabelRow("es", "")),
                defaultUnit = term("units", "piece"),
                tags = setOf(term("allergens", "egg"), term("food-groups", "pantry-staple"), term("allergens", "a")),
                purchasedWhole = true,
                imageUrl = "https://storage.example/egg.webp",
                archived = true,
            )

        assertEquals(
            IngredientDto(
                labels = mapOf("en" to "Egg"),
                defaultUnitTaxonomy = "units",
                defaultUnitTerm = "piece",
                tags = mapOf("allergens" to listOf("a", "egg"), "food-groups" to listOf("pantry-staple")),
                purchasedWhole = true,
                imageUrl = "https://storage.example/egg.webp",
                archived = true,
            ),
            draft.toDto(),
        )
    }

    @Test
    fun `an existing ingredient opens with its id fixed and a row for every seed language`() {
        val draft = ingredient("egg", "es" to "Huevo", archived = true).toDraft()

        assertFalse(draft.isNew)
        assertEquals(listOf(LabelRow("es", "Huevo"), LabelRow("en", "")), draft.labels)
        assertEquals(true, draft.archived)
    }
}
