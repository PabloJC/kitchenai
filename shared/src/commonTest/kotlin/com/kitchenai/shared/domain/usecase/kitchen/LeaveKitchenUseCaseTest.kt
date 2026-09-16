package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaveKitchenUseCaseTest {
    @Test
    fun `rejects the owner of a kitchen that still has other members`() =
        runTest {
            val shared = kitchen(ownerId = user, memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = shared)

            val result = LeaveKitchenUseCase(port)(user)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Validation)
            assertTrue(port.leftKitchens.isEmpty())
        }

    @Test
    fun `lets the owner leave a solo kitchen`() =
        runTest {
            val solo = kitchen(ownerId = user, memberIds = setOf(user))
            val port = FakeKitchenRepositoryContract(initial = solo)

            val result = LeaveKitchenUseCase(port)(user)

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(listOf(solo.id), port.leftKitchens)
        }

    @Test
    fun `lets a non-owner member leave a shared kitchen`() =
        runTest {
            val shared = kitchen(ownerId = otherUser, memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = shared)

            val result = LeaveKitchenUseCase(port)(user)

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(listOf(shared.id), port.leftKitchens)
        }

    @Test
    fun `returns NotFound when the user has no kitchen to leave`() =
        runTest {
            val port = FakeKitchenRepositoryContract(initial = null)

            val result = LeaveKitchenUseCase(port)(user)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.NotFound)
        }
}
