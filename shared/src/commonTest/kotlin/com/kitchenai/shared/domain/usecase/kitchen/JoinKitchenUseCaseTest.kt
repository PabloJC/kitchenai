package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoinKitchenUseCaseTest {
    @Test
    fun `joins directly when the user has no current kitchen`() =
        runTest {
            val target = kitchen(id = "kitchen-2", ownerId = otherUser, memberIds = setOf(otherUser, user))
            val port = FakeKitchenRepositoryContract(initial = null).apply { joinResult = AppResult.Success(target) }

            val result = JoinKitchenUseCase(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertEquals(AppResult.Success(target), result)
            assertTrue(port.leftKitchens.isEmpty())
            assertEquals(1, port.joinCalls)
        }

    @Test
    fun `leaves the current kitchen before joining another`() =
        runTest {
            val current = kitchen(id = "kitchen-1", ownerId = otherUser, memberIds = setOf(user, otherUser))
            val target = kitchen(id = "kitchen-2", ownerId = otherUser, memberIds = setOf(otherUser, user))
            val port = FakeKitchenRepositoryContract(initial = current).apply { joinResult = AppResult.Success(target) }

            val result = JoinKitchenUseCase(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertEquals(AppResult.Success(target), result)
            assertEquals(listOf(current.id), port.leftKitchens)
            assertEquals(1, port.joinCalls)
        }

    @Test
    fun `rejects when the requester owns a kitchen with other members`() =
        runTest {
            val owned = kitchen(ownerId = user, memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = owned)

            val result = JoinKitchenUseCase(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
            assertTrue(port.leftKitchens.isEmpty())
            assertEquals(0, port.joinCalls)
        }

    @Test
    fun `propagates a leave failure without attempting to join`() =
        runTest {
            val current = kitchen(ownerId = user, memberIds = setOf(user))
            val error = AppError.Network()
            val port = FakeKitchenRepositoryContract(initial = current).apply { leaveResult = AppResult.Failure(error) }

            val result = JoinKitchenUseCase(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertEquals(AppResult.Failure(error), result)
            assertEquals(0, port.joinCalls)
        }
}
