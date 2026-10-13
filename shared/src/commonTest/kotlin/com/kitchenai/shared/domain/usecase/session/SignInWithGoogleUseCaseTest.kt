package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SignInWithGoogleUseCaseTest {
    @Test
    fun `an anonymous session is linked, keeping its uid`() =
        runTest {
            val port = FakeSessionPort(anonymousUser)
            port.linkWithGoogleResult = AppResult.Success(googleUser)

            val result = SignInWithGoogleUseCase(port)(GoogleIdToken("id-token"))

            assertEquals(AppResult.Success(googleUser), result)
            assertEquals(anonymousUser.userId, (result as AppResult.Success).data.userId)
            assertEquals(1, port.linkWithGoogleCalls)
            assertEquals(0, port.signInWithGoogleCalls)
            assertEquals(googleUser, port.observeSession().first())
        }

    @Test
    fun `a Google account that already has a uid comes back as a conflict, and nothing is swapped`() =
        runTest {
            val conflict = AppError.Conflict()
            val port = FakeSessionPort(anonymousUser)
            port.linkWithGoogleResult = AppResult.Failure(conflict)

            val result = SignInWithGoogleUseCase(port)(GoogleIdToken("id-token"))

            assertTrue(result is AppResult.Failure)
            assertSame(conflict, result.error)
            assertEquals(0, port.signInWithGoogleCalls)
            assertEquals(anonymousUser, port.observeSession().first())
        }

    @Test
    fun `with no anonymous user to keep, it signs in`() =
        runTest {
            val port = FakeSessionPort(Session.SignedOut)
            port.signInWithGoogleResult = AppResult.Success(googleUser)

            val result = SignInWithGoogleUseCase(port)(GoogleIdToken("id-token"))

            assertEquals(AppResult.Success(googleUser), result)
            assertEquals(0, port.linkWithGoogleCalls)
            assertEquals(1, port.signInWithGoogleCalls)
        }

    @Test
    fun `an account already signed in with Google is not linked again`() =
        runTest {
            val port = FakeSessionPort(googleUser)
            port.signInWithGoogleResult = AppResult.Success(googleUser)

            SignInWithGoogleUseCase(port)(GoogleIdToken("id-token"))

            assertEquals(0, port.linkWithGoogleCalls)
            assertEquals(1, port.signInWithGoogleCalls)
        }

    @Test
    fun `forwards the access token to the port and none when the sheet had none`() =
        runTest {
            val port = FakeSessionPort(anonymousUser)

            SignInWithGoogleUseCase(port)(GoogleIdToken("id-token"), GoogleAccessToken("access-token"))
            assertEquals(GoogleAccessToken("access-token"), port.lastGoogleAccessToken)

            SignInWithGoogleUseCase(port)(GoogleIdToken("id-token"))
            assertEquals(null, port.lastGoogleAccessToken)
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

internal val googleUser = anonymousUser.copy(isAnonymous = false)
