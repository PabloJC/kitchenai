package com.kitchenai.admin.catalogue

import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.data.remote.dto.RecipeIngredientDto
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeReferencesTest {
    @Test
    fun `only recipes with a line naming the ingredient count, not free text that reads like it`() {
        val recipes =
            mapOf(
                "tortilla" to RecipeDto(ingredients = listOf(RecipeIngredientDto(ingredientId = "egg"))),
                "omelette" to RecipeDto(ingredients = listOf(RecipeIngredientDto(ingredientId = "egg"))),
                "salad" to RecipeDto(ingredients = listOf(RecipeIngredientDto(freeText = "egg"))),
            )

        assertEquals(listOf("omelette", "tortilla"), recipesReferencing("egg", recipes))
        assertEquals(emptyList(), recipesReferencing("rice", recipes))
    }
}
