package com.kitchenai.shared.domain.usecase.kitchen

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveKitchenUseCaseTest {
    @Test
    fun `streams the current kitchen and further updates to it`() =
        runTest {
            val first = kitchen(memberIds = setOf(user))
            val second = kitchen(memberIds = setOf(user, otherUser))
            val port = FakeKitchenRepositoryContract(initial = first)

            ObserveKitchenUseCase(port)(user).test {
                assertEquals(first, awaitItem())
                port.emit(second)
                assertEquals(second, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failing listener reports on errors and emits no kitchen`() =
        runTest {
            val useCase = ObserveKitchenUseCase(FakeKitchenRepositoryContract(readError = AppError.Unauthorized()))

            useCase(user).test { awaitComplete() }
            useCase.errors(user).test {
                assertTrue(awaitItem() is AppError.Unauthorized)
                awaitComplete()
            }
        }
}
