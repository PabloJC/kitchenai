package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferKitchenOwnershipUseCaseTest {
    private fun transfer(port: FakeKitchenRepositoryContract) =
        TransferKitchenOwnershipUseCase(port, KitchenMembershipLock())

    @Test
    fun `the owner hands the kitchen to a member and can then leave it`() =
        runTest {
            val shared = kitchen(ownerId = user, memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = shared)

            val result = transfer(port)(requesterId = user, newOwnerId = otherUser)

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(listOf(shared.id to otherUser), port.transfers)
            assertEquals(AppResult.Success(Unit), LeaveKitchenUseCase(port, KitchenMembershipLock())(user))
        }

    @Test
    fun `a member who is not the owner cannot hand it over`() =
        runTest {
            val port =
                FakeKitchenRepositoryContract(
                    initial = kitchen(ownerId = otherUser, memberIds = setOf(user, otherUser)),
                )

            val result = transfer(port)(requesterId = user, newOwnerId = user)

            assertTrue((result as AppResult.Failure).error is AppError.Unauthorized)
            assertTrue(port.transfers.isEmpty())
        }

    @Test
    fun `someone outside the kitchen cannot be made its owner`() =
        runTest {
            val port =
                FakeKitchenRepositoryContract(initial = kitchen(ownerId = user, memberIds = setOf(user, otherUser)))

            val result = transfer(port)(requesterId = user, newOwnerId = stranger)

            assertTrue((result as AppResult.Failure).error is AppError.NotFound)
            assertTrue(port.transfers.isEmpty())
        }

    @Test
    fun `the owner handing it to themself is refused`() =
        runTest {
            val port =
                FakeKitchenRepositoryContract(initial = kitchen(ownerId = user, memberIds = setOf(user, otherUser)))

            val result = transfer(port)(requesterId = user, newOwnerId = user)

            assertTrue((result as AppResult.Failure).error is AppError.Validation)
        }

    @Test
    fun `a failing read or write is reported`() =
        runTest {
            val unreadable = FakeKitchenRepositoryContract(readError = AppError.Network())
            assertTrue(transfer(unreadable)(user, otherUser) is AppResult.Failure)

            val failing =
                FakeKitchenRepositoryContract(initial = kitchen(ownerId = user, memberIds = setOf(user, otherUser)))
            failing.transferResult = AppResult.Failure(AppError.Network())
            assertTrue(transfer(failing)(user, otherUser) is AppResult.Failure)
        }
}
