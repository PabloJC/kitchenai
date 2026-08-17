package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreSuggestionsUseCaseTest {
    @Test
    fun `replaces the stored generation wholesale`() =
        runTest {
            val recipes = FakeRecipeRepositoryContract(stored = listOf(recipe(id = "recipe-1")))
            val useCase = StoreSuggestionsUseCase(recipes)

            useCase(listOf(recipe(id = "recipe-2")))

            assertEquals(listOf(recipe(id = "recipe-2")), recipes.getAll().let { (it as AppResult.Success).data })
        }

    @Test
    fun `a failing write is reported`() =
        runTest {
            val useCase = StoreSuggestionsUseCase(FakeRecipeRepositoryContract(writeError = AppError.Unknown()))

            assertTrue(useCase(listOf(recipe())) is AppResult.Failure)
        }
}
