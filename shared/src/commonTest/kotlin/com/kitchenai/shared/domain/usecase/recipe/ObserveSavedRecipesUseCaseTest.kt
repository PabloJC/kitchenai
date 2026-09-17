package com.kitchenai.shared.domain.usecase.recipe

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveSavedRecipesUseCaseTest {
    @Test
    fun `emits the recipes the kitchen has kept`() =
        runTest {
            val saved = listOf(recipe("recipe-1"), recipe("recipe-2"))

            ObserveSavedRecipesUseCase(FakeRecipeRepositoryContract(saved))(kitchen).test {
                assertEquals(saved, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failing listener reports on errors and emits no list`() =
        runTest {
            val useCase = ObserveSavedRecipesUseCase(FakeRecipeRepositoryContract(readError = AppError.Unauthorized()))

            useCase(kitchen).test { awaitComplete() }
            useCase.errors(kitchen).test {
                assertTrue(awaitItem() is AppError.Unauthorized)
                awaitComplete()
            }
        }
}
