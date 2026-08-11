package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClearCheckedItemsTest {
    private val list = listId()

    @Test
    fun `only the checked lines are dropped`() =
        runTest {
            val port = FakeShoppingItemPort()
            port.seed(list, shoppingItem("milk").copy(checked = true), shoppingItem("bread"))

            ClearCheckedItems(port)(userId(), list)

            assertEquals(listOf("bread"), port.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `the port failure travels back as it is`() =
        runTest {
            val result = ClearCheckedItems(FakeShoppingItemPort(AppError.Network()))(userId(), list)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Network)
        }
}
