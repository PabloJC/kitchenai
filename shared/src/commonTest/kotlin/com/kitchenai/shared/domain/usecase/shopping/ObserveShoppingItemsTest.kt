package com.kitchenai.shared.domain.usecase.shopping

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
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
            val port = FakeShoppingItemRepositoryContract()
            port.seed(
                list,
                shoppingItem("bought-late", seconds = 40).copy(checked = true),
                shoppingItem("added-late", seconds = 30),
                shoppingItem("bought-early", seconds = 20).copy(checked = true),
                shoppingItem("added-early", seconds = 10),
            )

            ObserveShoppingItems(port)(user, list).test {
                assertEquals(
                    listOf("added-early", "added-late", "bought-early", "bought-late"),
                    awaitItem().map { it.id.value },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failing listener reports on errors instead of emitting an empty list`() =
        runTest {
            val useCase = ObserveShoppingItems(FakeShoppingItemRepositoryContract(AppError.Network()))

            useCase(user, list).test { awaitComplete() }
            useCase.errors(user, list).test {
                assertTrue(awaitItem() is AppError.Network)
                awaitComplete()
            }
        }

    @Test
    fun `one list's broken listener is not reported on another list's errors`() =
        runTest {
            val port = FakeShoppingItemRepositoryContract(AppError.Network(), failingList = listId("other"))

            ObserveShoppingItems(port).errors(user, list).test { awaitComplete() }
        }
}
