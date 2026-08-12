package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveRecipeTest {
    private val stored = recipe(ingredients = listOf(recipeIngredient("ing-1")))

    @Test
    fun `keeps the recipe under the user`() =
        runTest {
            val port = FakeRecipePort()

            val result = SaveRecipe(port)(user, stored)

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(stored), port.recipes)
        }

    @Test
    fun `saving the same recipe twice leaves one copy`() =
        runTest {
            val port = FakeRecipePort()
            val useCase = SaveRecipe(port)

            useCase(user, stored)
            useCase(user, stored)

            assertEquals(1, port.recipes.size)
        }

    @Test
    fun `a recipe with no ingredients is rejected and nothing is written`() =
        runTest {
            val port = FakeRecipePort()

            val result = SaveRecipe(port)(user, recipe())

            assertTrue(result is AppResult.Failure)
            assertTrue(port.recipes.isEmpty())
        }

    @Test
    fun `a failing write is reported`() =
        runTest {
            val port = FakeRecipePort(writeError = AppError.Network())

            assertTrue(SaveRecipe(port)(user, stored) is AppResult.Failure)
        }
}
