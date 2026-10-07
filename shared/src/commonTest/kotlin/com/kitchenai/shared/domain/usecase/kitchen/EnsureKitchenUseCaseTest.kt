package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnsureKitchenUseCaseTest {
    @Test
    fun `creates a solo kitchen when none references this user yet`() =
        runTest {
            val solo = kitchen(memberIds = setOf(user))
            val port = FakeKitchenRepositoryContract().apply { createResult = AppResult.Success(solo) }

            val result = EnsureKitchenUseCase(port, KitchenMembershipLock())(user, displayName = "Ada")

            assertEquals(AppResult.Success(solo), result)
            assertEquals(1, port.createCalls)
        }

    @Test
    fun `does not create a kitchen when one already exists`() =
        runTest {
            val existing = kitchen()
            val port = FakeKitchenRepositoryContract(initial = existing)

            val result = EnsureKitchenUseCase(port, KitchenMembershipLock())(user, displayName = "Ada")

            assertEquals(AppResult.Success(existing), result)
            assertEquals(0, port.createCalls)
        }

    @Test
    fun `is idempotent - a second call reuses the kitchen the first one created`() =
        runTest {
            val solo = kitchen(memberIds = setOf(user))
            val port = FakeKitchenRepositoryContract().apply { createResult = AppResult.Success(solo) }
            val useCase = EnsureKitchenUseCase(port, KitchenMembershipLock())

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

            val result = EnsureKitchenUseCase(port, KitchenMembershipLock())(user, displayName = null)

            assertEquals(AppResult.Failure(error), result)
        }

    @Test
    fun `a read failure is propagated and is never mistaken for no kitchen yet`() =
        runTest {
            val error = AppError.Network()
            val port = FakeKitchenRepositoryContract(readError = error)

            val result = EnsureKitchenUseCase(port, KitchenMembershipLock())(user, displayName = "Ada")

            assertEquals(AppResult.Failure(error), result)
            assertEquals(0, port.createCalls)
        }

    @Test
    fun `leaving and then ensuring provisions a new kitchen carrying the given name`() =
        runTest {
            val lock = KitchenMembershipLock()
            val shared = kitchen(ownerId = otherUser, memberIds = setOf(user, otherUser))
            val solo = kitchen(id = "kitchen-2", memberIds = setOf(user))
            val port = FakeKitchenRepositoryContract(initial = shared).apply { createResult = AppResult.Success(solo) }

            assertEquals(AppResult.Success(Unit), LeaveKitchenUseCase(port, lock)(user))
            val result = EnsureKitchenUseCase(port, lock)(user, displayName = "Ada")

            assertEquals(AppResult.Success(solo), result)
            assertEquals(1, port.createCalls)
            assertEquals(solo, port.current)
        }

    @Test
    fun `an ensure waits for a join in flight and never leaves the user in two kitchens`() =
        runTest {
            val lock = KitchenMembershipLock()
            val target = kitchen(id = "kitchen-2", ownerId = otherUser, memberIds = setOf(otherUser, user))
            val port =
                FakeKitchenRepositoryContract(initial = null).apply {
                    joinResult = AppResult.Success(target)
                    createResult = AppResult.Success(kitchen(id = "kitchen-3", memberIds = setOf(user)))
                    joinGate = CompletableDeferred()
                }

            val join = async { JoinKitchenUseCase(port, lock)(user, "Ada", kitchenJoinCode("code-2")) }
            advanceUntilIdle()
            val ensure = async { EnsureKitchenUseCase(port, lock)(user, displayName = "Ada") }
            advanceUntilIdle()
            port.joinGate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(AppResult.Success(target), join.await())
            assertEquals(AppResult.Success(target), ensure.await())
            assertEquals(0, port.createCalls)
        }
}
