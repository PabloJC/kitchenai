package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AddStaplesUseCaseTest {
    private val now = Instant.fromEpochSeconds(1_000)

    @Test
    fun `each chosen staple is added once as a count of one in a single write`() =
        runTest {
            val port = FakePantryRepositoryContract()

            val result = useCase(port)(kitchen, listOf(ingredientId("rice"), ingredientId("egg"), ingredientId("rice")))

            assertEquals(AppResult.Success(2), result)
            assertEquals(1, port.upsertAllCalls)
            assertEquals(setOf("rice", "egg"), port.items.mapNotNull { it.ingredient?.value }.toSet())
            assertEquals(listOf(Quantity(1.0), Quantity(1.0)), port.items.map { it.quantity })
        }

    @Test
    fun `a staple already held is left as the user counted it`() =
        runTest {
            val port = FakePantryRepositoryContract(listOf(pantryItem("item-1", "rice", Quantity(500.0))))

            val result = useCase(port)(kitchen, listOf(ingredientId("rice"), ingredientId("egg")))

            assertEquals(AppResult.Success(1), result)
            assertEquals(Quantity(500.0), port.items.single { it.ingredient?.value == "rice" }.quantity)
        }

    @Test
    fun `nothing chosen or nothing new writes nothing`() =
        runTest {
            val port = FakePantryRepositoryContract(listOf(pantryItem("item-1", "rice", Quantity(1.0))))

            assertEquals(AppResult.Success(0), useCase(port)(kitchen, emptyList()))
            assertEquals(AppResult.Success(0), useCase(port)(kitchen, listOf(ingredientId("rice"))))
            assertEquals(0, port.upsertAllCalls)
        }

    @Test
    fun `a pantry that cannot be read is reported and nothing is written`() =
        runTest {
            val port = FakePantryRepositoryContract(readError = AppError.Network())

            val result = useCase(port)(kitchen, listOf(ingredientId("rice")))

            assertEquals(AppResult.Failure(AppError.Network()), result)
            assertEquals(0, port.upsertAllCalls)
        }

    @Test
    fun `a refused write is reported`() =
        runTest {
            val port = FakePantryRepositoryContract(writeError = AppError.Network())

            val result = useCase(port)(kitchen, listOf(ingredientId("rice")))

            assertEquals(AppResult.Failure(AppError.Network()), result)
        }

    private fun useCase(port: FakePantryRepositoryContract): AddStaplesUseCase {
        var next = 0
        return AddStaplesUseCase(port, IdGenerator { "generated-${++next}" }, TimeProvider { now })
    }
}
