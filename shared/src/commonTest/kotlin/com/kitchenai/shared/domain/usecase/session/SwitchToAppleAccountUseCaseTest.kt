package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SwitchToAppleAccountUseCaseTest {
    @Test
    fun `replaces an anonymous session with the Apple ID's own uid without linking`() =
        runTest {
            val existing =
                anonymousUser.copy(
                    userId = UserId.of("apple-user").getOrElse { error("valid fixture") },
                    isAnonymous = false,
                )
            val port = FakeSessionPort(anonymousUser)
            port.signInWithAppleResult = AppResult.Success(existing)

            val result = SwitchToAppleAccountUseCase(port)(AppleIdToken("id-token"), AppleRawNonce("nonce"))

            assertEquals(AppResult.Success(existing), result)
            assertEquals(0, port.linkWithAppleCalls)
            assertEquals(AppleRawNonce("nonce"), port.lastAppleNonce)
        }

    @Test
    fun `propagates the port Failure`() =
        runTest {
            val error = AppError.Network()
            val port = FakeSessionPort(anonymousUser)
            port.signInWithAppleResult = AppResult.Failure(error)

            val result = SwitchToAppleAccountUseCase(port)(AppleIdToken("id-token"), AppleRawNonce("nonce"))

            assertTrue(result is AppResult.Failure)
            assertSame(error, result.error)
        }
}
