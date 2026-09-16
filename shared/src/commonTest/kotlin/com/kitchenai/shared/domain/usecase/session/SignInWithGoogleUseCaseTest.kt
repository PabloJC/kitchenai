package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SignInWithGoogleUseCaseTest {
    @Test
    fun `returns the signed-in session the port produces`() =
        runTest {
            val port = FakeSessionPort(Session.SignedOut)
            port.signInWithGoogleResult = AppResult.Success(googleUser)

            val result = SignInWithGoogleUseCase(port)(GoogleIdToken("id-token"))

            assertEquals(AppResult.Success(googleUser), result)
        }

    @Test
    fun `propagates the port Failure without wrapping it in another error`() =
        runTest {
            val error = AppError.Unauthorized()
            val port = FakeSessionPort(Session.SignedOut)
            port.signInWithGoogleResult = AppResult.Failure(error)

            val result = SignInWithGoogleUseCase(port)(GoogleIdToken("id-token"))

            assertTrue(result is AppResult.Failure)
            assertSame(error, result.error)
        }
}

private val googleUser = anonymousUser.copy(isAnonymous = false)
