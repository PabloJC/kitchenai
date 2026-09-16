package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.usecase.UseCase

/** A thin pass-through: no idempotency check, since reaching this use case is itself a deliberate choice to sign in. */
class SignInWithGoogleUseCase(
    private val session: SessionRepositoryContract,
) : UseCase<GoogleIdToken, Session.SignedIn> {
    override suspend fun invoke(params: GoogleIdToken): AppResult<Session.SignedIn> = session.signInWithGoogle(params)
}
