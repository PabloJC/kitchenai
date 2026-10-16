package com.kitchenai.ui.presentation.suggestions.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeShareTextTest {
    private val labels =
        ShareLabels(
            servings = "4 servings",
            minutes = "25 min",
            ingredients = "Ingredients",
            steps = "Steps",
            optionalSuffix = " (optional)",
        )

    @Test
    fun `every line in one list with its amount and the steps numbered`() {
        val state =
            RecipeDetailUiState(
                title = "Paella",
                summary = "Rice with saffron",
                held = listOf(IngredientLineUi("rice", "400 g", optional = false)),
                missing = listOf(IngredientLineUi("saffron", null, optional = true)),
                unverifiable = listOf(IngredientLineUi("a pinch of salt", null, optional = false)),
                steps = listOf("Fry", "Simmer"),
            )

        val expected =
            """
            Paella
            Rice with saffron
            25 min · 4 servings

            Ingredients
            - 400 g rice
            - saffron (optional)
            - a pinch of salt

            Steps
            1. Fry
            2. Simmer
            """.trimIndent()
        assertEquals(expected, state.shareText(labels))
    }

    @Test
    fun `a recipe without minutes or summary or steps leaves no empty headings`() {
        val state =
            RecipeDetailUiState(title = "Toast", held = listOf(IngredientLineUi("bread", "2", optional = false)))

        val expected =
            """
            Toast
            4 servings

            Ingredients
            - 2 bread
            """.trimIndent()
        assertEquals(expected, state.shareText(labels.copy(minutes = null)))
    }
}
