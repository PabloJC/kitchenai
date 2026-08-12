package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.recipe.recipe
import com.kitchenai.shared.domain.usecase.recipe.recipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeScalingTest {
    private val unit = termRef("term-1")

    @Test
    fun `doubling the servings doubles every amount`() {
        val base =
            recipe(
                servings = 2,
                ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(100.0, unit))),
            )

        val scaled = base.scaledTo(4).unwrap()

        assertEquals(4, scaled.servings)
        assertEquals(listOf(Quantity(200.0, unit)), scaled.ingredients.map { it.quantity })
    }

    @Test
    fun `halving the servings halves every amount and keeps the unit`() {
        val base =
            recipe(
                servings = 4,
                ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(100.0, unit))),
            )

        val scaled = base.scaledTo(2).unwrap()

        assertEquals(listOf(Quantity(50.0, unit)), scaled.ingredients.map { it.quantity })
    }

    @Test
    fun `a line with no amount is left untouched`() {
        val line = recipeIngredient("ing-1")

        val scaled = recipe(servings = 2, ingredients = listOf(line)).scaledTo(6).unwrap()

        assertEquals(listOf(line), scaled.ingredients)
    }

    @Test
    fun `a free-text line is left untouched`() {
        val line = recipeIngredient(freeText = "free-text-1")

        val scaled = recipe(servings = 2, ingredients = listOf(line)).scaledTo(6).unwrap()

        assertEquals(listOf(line), scaled.ingredients)
    }

    @Test
    fun `fewer than one serving is rejected`() {
        val base = recipe(servings = 2, ingredients = listOf(recipeIngredient("ing-1")))

        assertTrue(base.scaledTo(0) is AppResult.Failure)
        assertTrue(base.scaledTo(-1) is AppResult.Failure)
    }

    private fun AppResult<Recipe>.unwrap(): Recipe = (this as AppResult.Success).data
}
