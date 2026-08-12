package com.kitchenai.shared.domain.usecase.recipe

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveSavedRecipesTest {
    @Test
    fun `emits the recipes the user has kept`() =
        runTest {
            val saved = listOf(recipe("recipe-1"), recipe("recipe-2"))

            ObserveSavedRecipes(FakeRecipePort(saved))(user).test {
                assertEquals(saved, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failing listener reports on errors and emits no list`() =
        runTest {
            val useCase = ObserveSavedRecipes(FakeRecipePort(readError = AppError.Unauthorized()))

            useCase(user).test { awaitComplete() }
            useCase.errors(user).test {
                assertTrue(awaitItem() is AppError.Unauthorized)
                awaitComplete()
            }
        }
}
