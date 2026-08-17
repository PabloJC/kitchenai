package com.kitchenai.shared.domain.usecase.pantry

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.port.IngredientRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveIngredientsUseCaseTest {
    private val catalogue = listOf(Ingredient(ingredientId("ing-1"), mapOf("en" to "ing-1"), null, emptyList()))

    @Test
    fun `emits the catalogue as the port publishes it`() =
        runTest {
            ObserveIngredientsUseCase(FakeIngredientPort(catalogue))().test {
                assertEquals(catalogue, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a failing catalogue listener reports on errors and emits nothing`() =
        runTest {
            val useCase = ObserveIngredientsUseCase(FakeIngredientPort(failure = AppError.Network()))

            useCase().test { awaitComplete() }
            useCase.errors().test {
                assertTrue(awaitItem() is AppError.Network)
                awaitComplete()
            }
        }

    private class FakeIngredientPort(
        private val answer: List<Ingredient> = emptyList(),
        private val failure: AppError? = null,
    ) : IngredientRepositoryContract {
        override fun observeIngredients(): Flow<List<Ingredient>> = if (failure == null) flowOf(answer) else emptyFlow()

        override fun ingredientErrors(): Flow<AppError> = failure?.let { flowOf(it) } ?: emptyFlow()

        // Unused here: this use case reads the catalogue stream and nothing else.
        override suspend fun getIngredient(id: IngredientId): AppResult<Ingredient> =
            AppResult.Failure(AppError.NotFound("Ingredient"))
    }
}
