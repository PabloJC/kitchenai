package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoveKitchenMemberUseCaseTest {
    @Test
    fun `removes a member when the requester is the owner`() =
        runTest {
            val shared = kitchen(ownerId = user, memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = shared)

            val result = RemoveKitchenMemberUseCase(port)(requesterId = user, memberId = otherUser)

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(listOf(shared.id to otherUser), port.removedMembers)
        }

    @Test
    fun `rejects when the requester is not the owner`() =
        runTest {
            val shared = kitchen(ownerId = otherUser, memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = shared)

            val result = RemoveKitchenMemberUseCase(port)(requesterId = user, memberId = otherUser)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Unauthorized)
            assertTrue(port.removedMembers.isEmpty())
        }

    @Test
    fun `returns NotFound when the requester has no kitchen`() =
        runTest {
            val port = FakeKitchenRepositoryContract(initial = null)

            val result = RemoveKitchenMemberUseCase(port)(requesterId = user, memberId = otherUser)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.NotFound)
        }
}
