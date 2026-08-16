package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * The seam towards authentication. [observeSession] is a bare `Flow<Session>`: a stream that
 * can carry a failure forces every consumer to handle an error it cannot act on, so
 * connection failures surface on the suspend calls instead.
 */
interface SessionRepositoryContract {
    fun observeSession(): Flow<Session>

    suspend fun signInAnonymously(): AppResult<Session.SignedIn>

    suspend fun signOut(): AppResult<Unit>
}
