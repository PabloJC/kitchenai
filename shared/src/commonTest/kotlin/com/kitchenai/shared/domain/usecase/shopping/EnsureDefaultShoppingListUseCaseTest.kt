package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnsureDefaultShoppingListUseCaseTest {
    private val kitchen = kitchenId()
    private val labels = mapOf("en" to "My list")
    private val port = FakeShoppingListRepositoryContract()
    private val useCase = EnsureDefaultShoppingListUseCase(port, sequentialIds(), fixedTime(1_000))

    @Test
    fun `two calls create exactly one list`() =
        runTest {
            val first = useCase(kitchen, labels)
            val second = useCase(kitchen, labels)

            assertEquals(1, port.upsertListCalls)
            assertTrue(first is AppResult.Success)
            assertTrue(second is AppResult.Success)
            assertEquals(first.data, second.data)
        }

    @Test
    fun `the label comes from the caller and is stored as given`() =
        runTest {
            useCase(kitchen, labels)

            val stored = port.observeLists(kitchen).first()
            assertEquals(listOf(labels), stored.map { it.labels })
            assertEquals(listOf(kitchen), stored.map { it.kitchenId })
        }
}
