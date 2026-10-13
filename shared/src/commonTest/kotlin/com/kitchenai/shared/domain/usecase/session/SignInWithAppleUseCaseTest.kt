package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SignInWithAppleUseCaseTest {
    @Test
    fun `an anonymous session is linked, keeping its uid`() =
        runTest {
            val port = FakeSessionPort(anonymousUser)
            port.linkWithAppleResult = AppResult.Success(appleUser)

            val result = SignInWithAppleUseCase(port)(AppleIdToken("id-token"), AppleRawNonce("nonce"))

            assertEquals(AppResult.Success(appleUser), result)
            assertEquals(1, port.linkWithAppleCalls)
            assertEquals(0, port.signInWithAppleCalls)
            assertEquals(AppleRawNonce("nonce"), port.lastAppleNonce)
            assertEquals(appleUser, port.observeSession().first())
        }

    @Test
    fun `an Apple ID that already has a uid comes back as a conflict, and nothing is swapped`() =
        runTest {
            val conflict = AppError.Conflict()
            val port = FakeSessionPort(anonymousUser)
            port.linkWithAppleResult = AppResult.Failure(conflict)

            val result = SignInWithAppleUseCase(port)(AppleIdToken("id-token"), AppleRawNonce("nonce"))

            assertTrue(result is AppResult.Failure)
            assertSame(conflict, result.error)
            assertEquals(0, port.signInWithAppleCalls)
            assertEquals(anonymousUser, port.observeSession().first())
        }

    @Test
    fun `with no anonymous user to keep, it signs in`() =
        runTest {
            val port = FakeSessionPort(Session.SignedOut)
            port.signInWithAppleResult = AppResult.Success(appleUser)

            val result = SignInWithAppleUseCase(port)(AppleIdToken("id-token"), AppleRawNonce("nonce"))

            assertEquals(AppResult.Success(appleUser), result)
            assertEquals(0, port.linkWithAppleCalls)
            assertEquals(1, port.signInWithAppleCalls)
        }

    @Test
    fun `propagates the port Failure without wrapping it in another error`() =
        runTest {
            val error = AppError.Network()
            val port = FakeSessionPort(anonymousUser)
            port.linkWithAppleResult = AppResult.Failure(error)

            val result = SignInWithAppleUseCase(port)(AppleIdToken("id-token"), AppleRawNonce("nonce"))

            assertTrue(result is AppResult.Failure)
            assertSame(error, result.error)
        }
}

private val appleUser = anonymousUser.copy(isAnonymous = false)
