package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import kotlinx.coroutines.flow.first

/**
 * Links first, like [SignInWithGoogleUseCase]: a guest keeps their uid and kitchen. An Apple ID that
 * already has a uid comes back as `AppError.Conflict`, and switching is [SwitchToAppleAccountUseCase]'s job.
 */
class SignInWithAppleUseCase(
    private val session: SessionRepositoryContract,
) {
    suspend operator fun invoke(
        token: AppleIdToken,
        rawNonce: AppleRawNonce,
    ): AppResult<Session.SignedIn> {
        val current = session.observeSession().first()
        return if (current is Session.SignedIn && current.isAnonymous) {
            session.linkWithApple(token, rawNonce)
        } else {
            session.signInWithApple(token, rawNonce)
        }
    }
}
