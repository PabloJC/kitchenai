package com.kitchenai.shared.domain.usecase.shopping

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveShoppingItemsTest {
    private val list = listId()
    private val user = userId()

    @Test
    fun `unchecked lines come first and each group is ordered by its last update`() =
        runTest {
            val port = FakeShoppingListPort()
            port.seed(
                list,
                shoppingItem("bought-late", seconds = 40).copy(checked = true),
                shoppingItem("added-late", seconds = 30),
                shoppingItem("bought-early", seconds = 20).copy(checked = true),
                shoppingItem("added-early", seconds = 10),
            )

            ObserveShoppingItems(port)(user, list).test {
                val emitted = awaitItem()
                assertTrue(emitted is AppResult.Success)
                assertEquals(
                    listOf("added-early", "added-late", "bought-early", "bought-late"),
                    emitted.data.map { it.id.value },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failing stream stays a Failure and is not turned into an empty list`() =
        runTest {
            ObserveShoppingItems(FakeShoppingListPort(AppError.Network()))(user, list).test {
                assertTrue(awaitItem() is AppResult.Failure)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
