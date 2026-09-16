package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnsureKitchenUseCaseTest {
    @Test
    fun `creates a solo kitchen when none references this user yet`() =
        runTest {
            val solo = kitchen(memberIds = setOf(user))
            val port = FakeKitchenRepositoryContract().apply { createResult = AppResult.Success(solo) }

            val result = EnsureKitchenUseCase(port)(user, displayName = "Ada")

            assertEquals(AppResult.Success(solo), result)
            assertEquals(1, port.createCalls)
        }

    @Test
    fun `does not create a kitchen when one already exists`() =
        runTest {
            val existing = kitchen()
            val port = FakeKitchenRepositoryContract(initial = existing)

            val result = EnsureKitchenUseCase(port)(user, displayName = "Ada")

            assertEquals(AppResult.Success(existing), result)
            assertEquals(0, port.createCalls)
        }

    @Test
    fun `is idempotent - a second call reuses the kitchen the first one created`() =
        runTest {
            val solo = kitchen(memberIds = setOf(user))
            val port = FakeKitchenRepositoryContract().apply { createResult = AppResult.Success(solo) }
            val useCase = EnsureKitchenUseCase(port)

            useCase(user, displayName = "Ada")
            val second = useCase(user, displayName = "Ada")

            assertEquals(AppResult.Success(solo), second)
            assertEquals(1, port.createCalls)
        }

    @Test
    fun `propagates the port Failure without wrapping it in another error`() =
        runTest {
            val error = AppError.Network()
            val port = FakeKitchenRepositoryContract().apply { createResult = AppResult.Failure(error) }

            val result = EnsureKitchenUseCase(port)(user, displayName = null)

            assertEquals(AppResult.Failure(error), result)
        }
}
