package com.kitchenai.shared.domain.usecase.shopping

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoveShoppingItemUseCaseTest {
    @Test
    fun `removing the same line twice leaves the rest of the list untouched`() =
        runTest {
            val list = listId()
            val port = FakeShoppingItemRepositoryContract()
            port.seed(list, shoppingItem("milk"), shoppingItem("bread"))

            RemoveShoppingItemUseCase(port)(kitchenId(), list, itemId("milk"))
            RemoveShoppingItemUseCase(port)(kitchenId(), list, itemId("milk"))

            assertEquals(listOf("bread"), port.itemsOf(list).map { it.id.value })
        }
}
