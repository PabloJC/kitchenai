package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreSuggestionsUseCaseTest {
    @Test
    fun `a new generation is stored beside the earlier one`() =
        runTest {
            val recipes = FakeRecipeRepositoryContract(stored = listOf(recipe(id = "recipe-1")))
            val useCase = StoreSuggestionsUseCase(recipes)

            useCase(listOf(recipe(id = "recipe-2")))

            assertEquals(
                listOf(listOf(recipe(id = "recipe-2")), listOf(recipe(id = "recipe-1"))),
                (recipes.getGenerations() as AppResult.Success).data,
            )
        }

    @Test
    fun `only the newest generations are kept`() =
        runTest {
            val recipes = FakeRecipeRepositoryContract()
            val useCase = StoreSuggestionsUseCase(recipes)

            (1..StoreSuggestionsUseCase.KEPT_GENERATIONS + 1).forEach { n -> useCase(listOf(recipe(id = "recipe-$n"))) }

            val kept = (recipes.getGenerations() as AppResult.Success).data.map { it.single().id.value }
            assertEquals(listOf("recipe-4", "recipe-3", "recipe-2"), kept)
        }

    @Test
    fun `a run that found nothing leaves the earlier generations alone`() =
        runTest {
            val recipes = FakeRecipeRepositoryContract(stored = listOf(recipe(id = "recipe-1")))

            StoreSuggestionsUseCase(recipes)(emptyList())

            assertEquals(listOf(listOf(recipe(id = "recipe-1"))), (recipes.getGenerations() as AppResult.Success).data)
        }

    @Test
    fun `a failing write is reported`() =
        runTest {
            val useCase = StoreSuggestionsUseCase(FakeRecipeRepositoryContract(writeError = AppError.Unknown()))

            assertTrue(useCase(listOf(recipe())) is AppResult.Failure)
        }
}
