package com.kitchenai.shared.domain.usecase.pantry

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Quantity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ObservePantryTest {
    private val expiry = Instant.fromEpochSeconds(3_000)
    private val older = Instant.fromEpochSeconds(10)
    private val newer = Instant.fromEpochSeconds(90)

    @Test
    fun `orders by expiry first then by the most recently touched and leaves undated rows last`() =
        runTest {
            val port =
                FakePantryPort(
                    listOf(
                        pantryItem("item-1", "ing-1", Quantity(1.0)),
                        pantryItem("item-2", "ing-2", Quantity(1.0), expiresAt = Instant.fromEpochSeconds(9_000)),
                        pantryItem("item-3", "ing-3", Quantity(1.0), expiresAt = expiry, updatedAt = older),
                        pantryItem("item-4", "ing-4", Quantity(1.0), expiresAt = expiry, updatedAt = newer),
                    ),
                )

            ObservePantry(port)(user).test {
                assertEquals(
                    listOf("item-4", "item-3", "item-2", "item-1").map(::pantryItemId),
                    awaitItem().map { it.id },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failing listener reports on errors and emits no list`() =
        runTest {
            val useCase = ObservePantry(FakePantryPort(readError = AppError.Unauthorized()))

            useCase(user).test { awaitComplete() }
            useCase.errors().test {
                assertTrue(awaitItem() is AppError.Unauthorized)
                awaitComplete()
            }
        }
}
