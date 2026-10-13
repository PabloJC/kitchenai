package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SwitchToGoogleAccountUseCaseTest {
    @Test
    fun `replaces an anonymous session with the Google account's own uid, without linking`() =
        runTest {
            val existing = googleUser.copy(userId = UserId.of("google-user").getOrElse { error("valid fixture") })
            val port = FakeSessionPort(anonymousUser)
            port.signInWithGoogleResult = AppResult.Success(existing)

            val result =
                SwitchToGoogleAccountUseCase(port)(GoogleIdToken("id-token"), GoogleAccessToken("access-token"))

            assertEquals(AppResult.Success(existing), result)
            assertEquals(0, port.linkWithGoogleCalls)
            assertEquals(GoogleAccessToken("access-token"), port.lastGoogleAccessToken)
        }

    @Test
    fun `propagates the port Failure`() =
        runTest {
            val error = AppError.Network()
            val port = FakeSessionPort(anonymousUser)
            port.signInWithGoogleResult = AppResult.Failure(error)

            val result = SwitchToGoogleAccountUseCase(port)(GoogleIdToken("id-token"))

            assertTrue(result is AppResult.Failure)
            assertSame(error, result.error)
        }
}
