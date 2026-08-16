package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetShoppingItemCheckedTest {
    private val list = listId()
    private val user = userId()
    private val port = FakeShoppingItemRepositoryContract()
    private val useCase = SetShoppingItemChecked(port, fixedTime(2_000))

    @Test
    fun `checking the same line twice leaves it checked`() =
        runTest {
            port.seed(list, shoppingItem("milk"))

            useCase(user, list, itemId("milk"), checked = true)
            useCase(user, list, itemId("milk"), checked = true)

            assertEquals(listOf(true), port.itemsOf(list).map { it.checked })
        }

    @Test
    fun `the value written is the one asked for and never the opposite of the stored one`() =
        runTest {
            port.seed(list, shoppingItem("milk").copy(checked = true))

            useCase(user, list, itemId("milk"), checked = true)
            useCase(user, list, itemId("milk"), checked = false)

            assertEquals(listOf(false), port.itemsOf(list).map { it.checked })
        }

    @Test
    fun `an unknown line fails with NotFound and writes nothing`() =
        runTest {
            port.seed(list, shoppingItem("milk"))

            val result = useCase(user, list, itemId("bread"), checked = true)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.NotFound)
            assertEquals(1, port.itemsOf(list).size)
        }
}
