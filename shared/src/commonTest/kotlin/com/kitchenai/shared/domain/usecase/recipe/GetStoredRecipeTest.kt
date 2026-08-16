package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetStoredRecipeTest {
    private val stored = recipe(id = "recipe-1")

    @Test
    fun `finds a recipe from the last generation by id`() =
        runTest {
            val useCase = GetStoredRecipe(FakeRecipeRepositoryContract(stored = listOf(stored)))

            assertEquals(AppResult.Success(stored), useCase(stored.id))
        }

    @Test
    fun `a recipe from no generation is absent rather than an error`() =
        runTest {
            val useCase = GetStoredRecipe(FakeRecipeRepositoryContract())

            assertEquals(AppResult.Success(null), useCase(stored.id))
        }

    @Test
    fun `a failing local read is reported`() =
        runTest {
            val useCase = GetStoredRecipe(FakeRecipeRepositoryContract(readError = AppError.Unknown()))

            assertTrue(useCase(stored.id) is AppResult.Failure)
        }
}
