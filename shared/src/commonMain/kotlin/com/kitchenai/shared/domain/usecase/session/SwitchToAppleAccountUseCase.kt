package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract

/** Replaces the session with the Apple ID's own uid; whatever the guest uid held stays behind it. */
class SwitchToAppleAccountUseCase(
    private val session: SessionRepositoryContract,
) {
    suspend operator fun invoke(
        token: AppleIdToken,
        rawNonce: AppleRawNonce,
    ): AppResult<Session.SignedIn> = session.signInWithApple(token, rawNonce)
}
