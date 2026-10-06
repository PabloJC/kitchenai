package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinKitchenUseCaseTest {
    private fun useCaseFor(port: FakeKitchenRepositoryContract) = JoinKitchenUseCase(port, KitchenMembershipLock())

    @Test
    fun `joins directly when the user has no current kitchen`() =
        runTest {
            val target = kitchen(id = "kitchen-2", ownerId = otherUser, memberIds = setOf(otherUser, user))
            val port = FakeKitchenRepositoryContract(initial = null).apply { joinResult = AppResult.Success(target) }

            val result = useCaseFor(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertEquals(AppResult.Success(target), result)
            assertNull(port.joins.single().leaving)
            assertTrue(port.leftKitchens.isEmpty())
        }

    @Test
    fun `hands the current kitchen to the join so both happen as one write`() =
        runTest {
            val current = kitchen(id = "kitchen-1", ownerId = otherUser, memberIds = setOf(user, otherUser))
            val target = kitchen(id = "kitchen-2", ownerId = otherUser, memberIds = setOf(otherUser, user))
            val port = FakeKitchenRepositoryContract(initial = current).apply { joinResult = AppResult.Success(target) }

            val result = useCaseFor(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertEquals(AppResult.Success(target), result)
            assertEquals(current.id, port.joins.single().leaving)
            assertEquals(listOf(current.id), port.leftKitchens)
            assertEquals(target, port.current)
        }

    @Test
    fun `a code that does not resolve keeps the current kitchen`() =
        runTest {
            val current = kitchen(id = "kitchen-1", ownerId = user, memberIds = setOf(user))
            val error = AppError.NotFound("kitchenInvite")
            val port = FakeKitchenRepositoryContract(initial = current).apply { joinResult = AppResult.Failure(error) }

            val result = useCaseFor(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("bad-code"))

            assertEquals(AppResult.Failure(error), result)
            assertTrue(port.leftKitchens.isEmpty())
            assertEquals(current, port.current)
        }

    @Test
    fun `a kitchen that refuses the caller keeps the current kitchen`() =
        runTest {
            val current = kitchen(id = "kitchen-1", ownerId = otherUser, memberIds = setOf(user, otherUser))
            val refusal = AppResult.Failure(AppError.Unauthorized())
            val port = FakeKitchenRepositoryContract(initial = current).apply { joinResult = refusal }

            val result = useCaseFor(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertTrue(result is AppResult.Failure)
            assertEquals(current, port.current)
        }

    @Test
    fun `the display name travels with the join`() =
        runTest {
            val target = kitchen(id = "kitchen-2", ownerId = otherUser, memberIds = setOf(otherUser, user))
            val port = FakeKitchenRepositoryContract(initial = null).apply { joinResult = AppResult.Success(target) }

            useCaseFor(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))
            useCaseFor(port)(user, displayName = null, joinCode = kitchenJoinCode("code-2"))

            assertEquals(listOf("Ada", null), port.joins.map { it.displayName })
        }

    @Test
    fun `rejects when the requester owns a kitchen with other members`() =
        runTest {
            val owned = kitchen(ownerId = user, memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = owned)

            val result = useCaseFor(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
            assertEquals(0, port.joinCalls)
            assertEquals(owned, port.current)
        }

    @Test
    fun `a read failure is propagated and is never mistaken for no current kitchen`() =
        runTest {
            val error = AppError.Network()
            val port = FakeKitchenRepositoryContract(readError = error)

            val result = useCaseFor(port)(user, displayName = "Ada", joinCode = kitchenJoinCode("code-2"))

            assertEquals(AppResult.Failure(error), result)
            assertEquals(0, port.joinCalls)
        }
}
