package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.TimeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class UpdatePantryItemTest {
    private val unitA = termRef("term-a")
    private val now = Instant.fromEpochSeconds(1_000)
    private val held = pantryItem("item-1", "ing-1", Quantity(200.0, unitA))
    private val port = FakePantryRepositoryContract(listOf(held))
    private val useCase = UpdatePantryItem(port, TimeProvider { now })

    @Test
    fun `writes the item and restamps it`() =
        runTest {
            val result = useCase(user, held.copy(quantity = Quantity(120.0, unitA)))

            assertTrue(result is AppResult.Success)
            assertEquals(Quantity(120.0, unitA), port.items.single().quantity)
            assertEquals(now, result.data.updatedAt)
        }

    @Test
    fun `rejects an amount of zero because a removal has to be explicit`() =
        runTest {
            val result = useCase(user, held.copy(quantity = Quantity(0.0, unitA)))

            assertTrue(result is AppResult.Failure)
            assertEquals("amount", (result.error as AppError.Validation).field)
            assertEquals(0, port.upsertCalls)
        }

    @Test
    fun `rejects a negative amount`() =
        runTest {
            val result = useCase(user, held.copy(quantity = Quantity(-1.0, unitA)))

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
        }

    @Test
    fun `propagates a write failure`() =
        runTest {
            val failing = FakePantryRepositoryContract(listOf(held), writeError = AppError.Network())

            val result = UpdatePantryItem(failing, TimeProvider { now })(user, held)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Network)
        }
}
