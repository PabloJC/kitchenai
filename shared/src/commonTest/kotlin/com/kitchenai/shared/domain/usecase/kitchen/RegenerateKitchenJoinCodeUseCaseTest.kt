package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegenerateKitchenJoinCodeUseCaseTest {
    @Test
    fun `regenerates the join code when the requester is the owner`() =
        runTest {
            val shared = kitchen(ownerId = user, memberIds = setOf(user, otherUser), joinCode = "old-code")
            val regenerated = shared.copy(joinCode = kitchenJoinCode("new-code"))
            val port =
                FakeKitchenRepositoryContract(initial = shared).apply {
                    regenerateResult = AppResult.Success(regenerated)
                }

            val result = RegenerateKitchenJoinCodeUseCase(port)(user)

            assertEquals(AppResult.Success(regenerated), result)
            assertEquals(1, port.regenerateCalls)
        }

    @Test
    fun `rejects when the requester is not the owner`() =
        runTest {
            val shared = kitchen(ownerId = otherUser, memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = shared)

            val result = RegenerateKitchenJoinCodeUseCase(port)(user)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Unauthorized)
            assertEquals(0, port.regenerateCalls)
        }

    @Test
    fun `returns NotFound when the requester has no kitchen`() =
        runTest {
            val port = FakeKitchenRepositoryContract(initial = null)

            val result = RegenerateKitchenJoinCodeUseCase(port)(user)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.NotFound)
        }

    @Test
    fun `a read failure is propagated and is never mistaken for no kitchen`() =
        runTest {
            val error = AppError.Network()
            val port = FakeKitchenRepositoryContract(readError = error)

            val result = RegenerateKitchenJoinCodeUseCase(port)(user)

            assertEquals(AppResult.Failure(error), result)
            assertEquals(0, port.regenerateCalls)
        }
}
