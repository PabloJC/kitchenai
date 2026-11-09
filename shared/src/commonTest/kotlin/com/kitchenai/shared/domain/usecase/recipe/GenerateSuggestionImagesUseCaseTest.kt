package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Recipe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateSuggestionImagesUseCaseTest {
    private val landed = mutableListOf<Recipe>()

    private fun keyed(id: String) = recipe(id).copy(imageKey = id.padEnd(64, 'a'))

    private fun useCase(port: FakeRecipeRepositoryContract) =
        GenerateSuggestionImagesUseCase(GenerateRecipeImageUseCase(port))

    @Test
    fun `each keyed recipe without a photo gets one in list order`() =
        runTest {
            val port = FakeRecipeRepositoryContract()
            val recipes = listOf(keyed("r1"), keyed("r2"), keyed("r3"))

            useCase(port)(kitchen, recipes) { landed += it }

            assertEquals(recipes, port.imageRequests)
            assertEquals(recipes.map { it.id }, landed.map { it.id })
            assertTrue(landed.all { it.imageUrl != null })
        }

    @Test
    fun `a recipe with a photo or no key makes no call`() =
        runTest {
            val port = FakeRecipeRepositoryContract()
            val pictured = keyed("r1").copy(imageUrl = "https://images.test/old.jpg")

            useCase(port)(kitchen, listOf(pictured, recipe("r2"))) { landed += it }

            assertTrue(port.imageRequests.isEmpty())
            assertTrue(landed.isEmpty())
        }

    @Test
    fun `no more than two calls are in flight at once`() =
        runTest {
            var inFlight = 0
            var most = 0
            val port =
                FakeRecipeRepositoryContract(
                    imageAnswer = {
                        inFlight++
                        most = maxOf(most, inFlight)
                        delay(100)
                        inFlight--
                        AppResult.Success("https://images.test/${it.id.value}.jpg")
                    },
                )

            useCase(port)(kitchen, List(5) { keyed("r$it") }) { landed += it }

            assertEquals(2, most)
            assertEquals(5, landed.size)
        }

    @Test
    fun `a failure in the middle skips that recipe and the rest carry on`() =
        runTest {
            val port =
                FakeRecipeRepositoryContract(
                    imageAnswer = {
                        if (it.id.value == "r2") {
                            AppResult.Failure(AppError.Network(null))
                        } else {
                            AppResult.Success("https://images.test/${it.id.value}.jpg")
                        }
                    },
                )

            useCase(port)(kitchen, listOf(keyed("r1"), keyed("r2"), keyed("r3"))) { landed += it }

            assertEquals(listOf("r1", "r3"), landed.map { it.id.value })
        }
}
