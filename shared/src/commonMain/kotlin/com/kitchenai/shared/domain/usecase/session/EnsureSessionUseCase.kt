package com.kitchenai.shared.domain.usecase.session

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.usecase.NoParams
import com.kitchenai.shared.domain.usecase.UseCase
import kotlinx.coroutines.flow.first

/**
 * Signs in anonymously only when there is no session. Idempotent by design: a second call on
 * an already signed-in user must not create a second anonymous account.
 */
class EnsureSessionUseCase(
    private val session: SessionRepositoryContract,
) : UseCase<NoParams, Session.SignedIn> {
    override suspend fun invoke(params: NoParams): AppResult<Session.SignedIn> =
        when (val current = session.observeSession().first()) {
            is Session.SignedIn -> AppResult.Success(current)
            Session.SignedOut -> session.signInAnonymously()
        }
}
