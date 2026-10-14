package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.usecase.UseCase
import kotlinx.coroutines.flow.first

/**
 * Links first: an anonymous user keeps their uid, and so their kitchen. A Google account that already
 * has a uid comes back as `AppError.Conflict`; replacing the session is [SwitchToGoogleAccountUseCase]'s
 * job, taken only once the user agreed to leave the guest data behind (#250).
 */
class SignInWithGoogleUseCase(
    private val session: SessionRepositoryContract,
) : UseCase<GoogleIdToken, Session.SignedIn> {
    override suspend fun invoke(params: GoogleIdToken): AppResult<Session.SignedIn> = invoke(params, null)

    /** iOS hands the access token over as well: its Firebase credential factory cannot be built without it. */
    suspend operator fun invoke(
        token: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> {
        val current = session.observeSession().first()
        return if (current is Session.SignedIn && current.isAnonymous) {
            session.linkWithGoogle(token, accessToken)
        } else {
            session.signInWithGoogle(token, accessToken)
        }
    }
}
