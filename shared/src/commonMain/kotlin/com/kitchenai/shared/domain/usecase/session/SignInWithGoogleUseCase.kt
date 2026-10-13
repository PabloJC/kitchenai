package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.usecase.UseCase

/** A thin pass-through: no idempotency check, since reaching this use case is itself a deliberate choice to sign in. */
class SignInWithGoogleUseCase(
    private val session: SessionRepositoryContract,
) : UseCase<GoogleIdToken, Session.SignedIn> {
    override suspend fun invoke(params: GoogleIdToken): AppResult<Session.SignedIn> = invoke(params, null)

    /** iOS hands the access token over as well: its Firebase credential factory cannot be built without it. */
    suspend operator fun invoke(
        token: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> = session.signInWithGoogle(token, accessToken)
}
