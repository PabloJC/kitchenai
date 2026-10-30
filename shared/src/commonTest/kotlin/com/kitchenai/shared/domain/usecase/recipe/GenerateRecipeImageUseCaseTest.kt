package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateRecipeImageUseCaseTest {
    private val key = "a".repeat(64)

    @Test
    fun `a recipe with a key gets its photograph`() =
        runTest {
            val port = FakeRecipeRepositoryContract()
            val keyed = recipe().copy(imageKey = key)

            val result = GenerateRecipeImageUseCase(port)(kitchen, keyed)

            assertEquals(AppResult.Success(keyed.copy(imageUrl = "https://images.test/recipe.jpg")), result)
            assertEquals(listOf(keyed), port.imageRequests)
        }

    @Test
    fun `a recipe that already has a photograph is not asked for again`() =
        runTest {
            val port = FakeRecipeRepositoryContract()
            val pictured = recipe().copy(imageKey = key, imageUrl = "https://images.test/old.jpg")

            val result = GenerateRecipeImageUseCase(port)(kitchen, pictured)

            assertEquals(AppResult.Success(pictured), result)
            assertTrue(port.imageRequests.isEmpty())
        }

    @Test
    fun `a recipe with no key is returned untouched without a call`() =
        runTest {
            val port = FakeRecipeRepositoryContract()
            val unkeyed = recipe()

            val result = GenerateRecipeImageUseCase(port)(null, unkeyed)

            assertEquals(AppResult.Success(unkeyed), result)
            assertTrue(port.imageRequests.isEmpty())
        }

    @Test
    fun `a failed image call is reported rather than swallowed`() =
        runTest {
            val port = FakeRecipeRepositoryContract(imageResult = AppResult.Failure(AppError.Network(null)))

            val result = GenerateRecipeImageUseCase(port)(kitchen, recipe().copy(imageKey = key))

            assertEquals(AppResult.Failure(AppError.Network(null)), result)
        }
}
