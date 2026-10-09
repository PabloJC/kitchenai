package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetShoppingItemQuantityUseCaseTest {
    private val list = listId()
    private val kitchen = kitchenId()
    private val unit = termRef("units", "kilogram")
    private val port = FakeShoppingItemRepositoryContract()
    private val useCase = SetShoppingItemQuantityUseCase(port, fixedTime(2_000))

    @Test
    fun `a line typed without an amount is given one`() =
        runTest {
            port.seed(list, shoppingItem("milk", ingredient = null, freeText = "milk"))

            val result = useCase(kitchen, list, itemId("milk"), Quantity(2.0, unit))

            assertTrue(result is AppResult.Success)
            val stored = port.itemsOf(list).single()
            assertEquals(Quantity(2.0, unit), stored.quantity)
            assertEquals(instant(2_000), stored.updatedAt)
            assertEquals("milk", stored.freeText)
        }

    @Test
    fun `an existing amount is replaced and not added to`() =
        runTest {
            port.seed(list, shoppingItem("flour", quantity = Quantity(1.0, unit)))

            useCase(kitchen, list, itemId("flour"), Quantity(3.0, unit))

            assertEquals(Quantity(3.0, unit), port.itemsOf(list).single().quantity)
        }

    @Test
    fun `null clears the amount and the checked state is kept`() =
        runTest {
            port.seed(list, shoppingItem("flour", quantity = Quantity(1.0, unit)).copy(checked = true))

            useCase(kitchen, list, itemId("flour"), null)

            val stored = port.itemsOf(list).single()
            assertNull(stored.quantity)
            assertTrue(stored.checked)
        }

    @Test
    fun `an amount that is not positive is rejected and nothing is written`() =
        runTest {
            port.seed(list, shoppingItem("flour"))

            listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
                val result = useCase(kitchen, list, itemId("flour"), Quantity(amount, unit))

                assertTrue(result is AppResult.Failure)
                assertTrue(result.error is AppError.Validation)
            }
            assertNull(port.itemsOf(list).single().quantity)
        }

    @Test
    fun `an unknown line fails with NotFound and writes nothing`() =
        runTest {
            port.seed(list, shoppingItem("milk"))

            val result = useCase(kitchen, list, itemId("bread"), Quantity(1.0, unit))

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.NotFound)
            assertNull(port.itemsOf(list).single().quantity)
        }
}
