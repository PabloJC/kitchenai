package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.usecase.NoParams
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SignOutUseCaseTest {
    @Test
    fun `leaves the port with no session`() =
        runTest {
            val port = FakeSessionPort(anonymousUser)

            val result = SignOutUseCase(port)(NoParams)

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(Session.SignedOut, ObserveSessionUseCase(port)().first())
        }

    @Test
    fun `propagates the port Failure without wrapping it in another error`() =
        runTest {
            val error = AppError.Unauthorized()
            val port = FakeSessionPort(anonymousUser).apply { signOutResult = AppResult.Failure(error) }

            val result = SignOutUseCase(port)(NoParams)

            assertTrue(result is AppResult.Failure)
            assertSame(error, result.error)
        }
}
