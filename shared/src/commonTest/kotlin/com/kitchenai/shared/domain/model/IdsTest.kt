package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdsTest {
    @Test
    fun `a non-blank identifier is accepted and keeps its value`() {
        val id = UserId.of("user-1")
        assertTrue(id is AppResult.Success)
        assertEquals("user-1", id.data.value)
    }

    @Test
    fun `an empty identifier is rejected as a Validation failure`() {
        val id = RecipeId.of("")
        assertTrue(id is AppResult.Failure)
        assertEquals(AppError.Validation("RecipeId", "must not be blank"), id.error)
    }

    @Test
    fun `a whitespace-only identifier is rejected too`() {
        val id = IngredientId.of("   ")
        assertTrue(id is AppResult.Failure)
        assertTrue(id.error is AppError.Validation)
    }

    @Test
    fun `every identifier type rejects a blank string without throwing`() {
        val results =
            listOf(
                UserId.of(""),
                IngredientId.of(""),
                PantryItemId.of(""),
                ShoppingListId.of(""),
                ShoppingItemId.of(""),
                RecipeId.of(""),
                AgentId.of(""),
                TaxonomyId.of(""),
                TermId.of(""),
            )
        assertTrue(results.all { it is AppResult.Failure })
    }

    @Test
    fun `TermRef equality is structural`() {
        val taxonomy = "diet"
        assertEquals(termRef(taxonomy, "vegan"), termRef(taxonomy, "vegan"))
        assertNotEquals(termRef(taxonomy, "vegan"), termRef(taxonomy, "pescatarian"))
        assertNotEquals(termRef(taxonomy, "vegan"), termRef("allergen", "vegan"))
    }

    private fun termRef(
        taxonomy: String,
        term: String,
    ): TermRef =
        TermRef(
            (TaxonomyId.of(taxonomy) as AppResult.Success).data,
            (TermId.of(term) as AppResult.Success).data,
        )
}
