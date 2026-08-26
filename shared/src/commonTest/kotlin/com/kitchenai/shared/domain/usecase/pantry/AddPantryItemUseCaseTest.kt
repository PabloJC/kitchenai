package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class AddPantryItemUseCaseTest {
    private val unitA = termRef("term-a")
    private val unitB = termRef("term-b")
    private val now = Instant.fromEpochSeconds(1_000)
    private val later = Instant.fromEpochSeconds(9_000)
    private val sooner = Instant.fromEpochSeconds(5_000)

    private fun useCase(port: FakePantryRepositoryContract) =
        AddPantryItemUseCase(port, IdGenerator { "generated-1" }, TimeProvider { now })

    @Test
    fun `tops up the held row when the ingredient and the unit match`() =
        runTest {
            val held = pantryItem("item-1", "ing-1", Quantity(200.0, unitA), expiresAt = later)
            val port = FakePantryRepositoryContract(listOf(held))

            val result = useCase(port)(user, ingredientId("ing-1"), null, Quantity(50.0, unitA), null, sooner)

            assertTrue(result is AppResult.Success)
            assertEquals(1, port.items.size)
            assertEquals(Quantity(250.0, unitA), port.items.single().quantity)
            assertEquals(sooner, port.items.single().expiresAt)
            assertEquals(now, port.items.single().updatedAt)
        }

    @Test
    fun `keeps a separate row when the unit differs because nothing is converted`() =
        runTest {
            val port = FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(200.0, unitA))))

            useCase(port)(user, ingredientId("ing-1"), null, Quantity(1.0, unitB), null, null)

            assertEquals(2, port.items.size)
        }

    @Test
    fun `a new holding takes the generated id and the current time`() =
        runTest {
            val result =
                useCase(FakePantryRepositoryContract())(user, ingredientId("ing-1"), null, Quantity(3.0), null, null)

            assertTrue(result is AppResult.Success)
            assertEquals(pantryItemId("generated-1"), result.data.id)
            assertEquals(now, result.data.updatedAt)
        }

    @Test
    fun `rejects an amount of zero without touching the port`() =
        runTest {
            val port = FakePantryRepositoryContract()

            val result = useCase(port)(user, ingredientId("ing-1"), null, Quantity(0.0, unitA), null, null)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
            assertEquals(0, port.upsertCalls)
        }

    @Test
    fun `propagates a write failure instead of reporting the item as added`() =
        runTest {
            val port = FakePantryRepositoryContract(writeError = AppError.Network())

            val result = useCase(port)(user, ingredientId("ing-1"), null, Quantity(3.0), null, null)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Network)
        }

    @Test
    fun `a free-text holding is added even though the catalogue has never heard of it`() =
        runTest {
            val port = FakePantryRepositoryContract()

            val result = useCase(port)(user, null, "the good bread", Quantity(1.0), null, null)

            assertTrue(result is AppResult.Success)
            assertEquals("the good bread", result.data.freeText)
            assertEquals(null, result.data.ingredient)
        }

    @Test
    fun `a free-text holding never merges with an existing one`() =
        runTest {
            val existing = freeTextPantryItem("item-1", "the good bread", Quantity(1.0))
            val port = FakePantryRepositoryContract(listOf(existing))

            useCase(port)(user, null, "the good bread", Quantity(1.0), null, null)

            assertEquals(2, port.items.size)
        }

    @Test
    fun `rejects both an ingredient and free text on the same holding`() =
        runTest {
            val port = FakePantryRepositoryContract()

            val result = useCase(port)(user, ingredientId("ing-1"), "the good bread", Quantity(1.0), null, null)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
            assertEquals(0, port.upsertCalls)
        }
}
