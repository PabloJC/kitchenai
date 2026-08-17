package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClearCheckedItemsUseCaseTest {
    private val list = listId()

    @Test
    fun `only the checked lines are dropped`() =
        runTest {
            val port = FakeShoppingItemRepositoryContract()
            port.seed(list, shoppingItem("milk").copy(checked = true), shoppingItem("bread"))

            ClearCheckedItemsUseCase(port)(userId(), list)

            assertEquals(listOf("bread"), port.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `the port failure travels back as it is`() =
        runTest {
            val useCase = ClearCheckedItemsUseCase(FakeShoppingItemRepositoryContract(AppError.Network()))
            val result = useCase(userId(), list)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Network)
        }
}
