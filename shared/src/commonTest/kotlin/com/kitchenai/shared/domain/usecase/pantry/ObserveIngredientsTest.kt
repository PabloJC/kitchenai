package com.kitchenai.shared.domain.usecase.pantry

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.port.IngredientPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveIngredientsTest {
    private val catalogue = listOf(Ingredient(ingredientId("ing-1"), mapOf("en" to "ing-1"), null, emptyList()))

    @Test
    fun `emits the catalogue as the port publishes it`() =
        runTest {
            ObserveIngredients(FakeIngredientPort(AppResult.Success(catalogue)))().test {
                assertEquals(AppResult.Success(catalogue), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `passes a catalogue failure through`() =
        runTest {
            ObserveIngredients(FakeIngredientPort(AppResult.Failure(AppError.Network())))().test {
                assertTrue(awaitItem() is AppResult.Failure)
                awaitComplete()
            }
        }

    private class FakeIngredientPort(
        private val answer: AppResult<List<Ingredient>>,
    ) : IngredientPort {
        override fun observeIngredients(): Flow<AppResult<List<Ingredient>>> = flowOf(answer)

        // Unused here: this use case reads the catalogue stream and nothing else.
        override suspend fun getIngredient(id: IngredientId): AppResult<Ingredient> =
            AppResult.Failure(AppError.NotFound("Ingredient"))
    }
}
