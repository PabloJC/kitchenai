package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnsureDefaultShoppingListTest {
    private val user = userId()
    private val labels = mapOf("en" to "My list")
    private val port = FakeShoppingListPort()
    private val useCase = EnsureDefaultShoppingList(port, sequentialIds(), fixedTime(1_000))

    @Test
    fun `two calls create exactly one list`() =
        runTest {
            val first = useCase(user, labels)
            val second = useCase(user, labels)

            assertEquals(1, port.upsertListCalls)
            assertTrue(first is AppResult.Success)
            assertTrue(second is AppResult.Success)
            assertEquals(first.data, second.data)
        }

    @Test
    fun `the label comes from the caller and is stored as given`() =
        runTest {
            useCase(user, labels)

            val stored = port.observeLists(user).first()
            assertEquals(listOf(labels), stored.map { it.labels })
            assertEquals(listOf(user), stored.map { it.ownerId })
        }
}
