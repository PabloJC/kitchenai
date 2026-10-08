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
    fun `a typed join code is trimmed and lower case`() {
        val code = KitchenJoinCode.normalised("  1A2B-C3D4  ")

        assertEquals("1a2b-c3d4", (code as AppResult.Success).data.value)
    }

    @Test
    fun `an upper case code made on one platform equals the lower case one made on another`() {
        val upper = (KitchenJoinCode.normalised("AB12-CD34") as AppResult.Success).data
        val lower = (KitchenJoinCode.normalised("ab12-cd34") as AppResult.Success).data

        assertEquals(lower, upper)
    }

    @Test
    fun `a blank join code is rejected whichever way it is built`() {
        assertTrue(KitchenJoinCode.normalised("   ") is AppResult.Failure)
        assertTrue(KitchenJoinCode.of("   ") is AppResult.Failure)
    }

    @Test
    fun `a stored join code keeps its case`() {
        val code = KitchenJoinCode.of("AB12-CD34")

        assertEquals("AB12-CD34", (code as AppResult.Success).data.value)
    }

    @Test
    fun `an invite is looked up lower case first and then in the upper case older iOS builds wrote`() {
        val code = (KitchenJoinCode.normalised("Ab12-Cd34") as AppResult.Success).data

        assertEquals(listOf("ab12-cd34", "AB12-CD34"), code.storedForms().map { it.value })
    }

    @Test
    fun `a code with no letters has a single form`() {
        val code = (KitchenJoinCode.normalised("1234-5678") as AppResult.Success).data

        assertEquals(listOf("1234-5678"), code.storedForms().map { it.value })
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
