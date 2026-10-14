package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.usecase.UseCase

/** Replaces the session with the Google account's own uid; whatever the guest uid held stays behind it. */
class SwitchToGoogleAccountUseCase(
    private val session: SessionRepositoryContract,
) : UseCase<GoogleIdToken, Session.SignedIn> {
    override suspend fun invoke(params: GoogleIdToken): AppResult<Session.SignedIn> = invoke(params, null)

    suspend operator fun invoke(
        token: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> = session.signInWithGoogle(token, accessToken)
}
