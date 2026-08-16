package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemovePantryItemTest {
    private val held = listOf(pantryItem("item-1", "ing-1", Quantity(1.0)))

    @Test
    fun `drops the holding from the pantry`() =
        runTest {
            val port = FakePantryRepositoryContract(held)

            val result = RemovePantryItem(port)(user, pantryItemId("item-1"))

            assertTrue(result is AppResult.Success)
            assertEquals(emptyList(), port.items)
        }

    @Test
    fun `propagates the port failure`() =
        runTest {
            val port = FakePantryRepositoryContract(held, writeError = AppError.Network())

            val result = RemovePantryItem(port)(user, pantryItemId("item-1"))

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Network)
        }
}
