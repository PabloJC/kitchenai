package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
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

    /** Always starts a fresh session: the previous anonymous account, if any, is left behind unmerged. */
    suspend fun signInWithGoogle(
        idToken: GoogleIdToken,
        accessToken: GoogleAccessToken? = null,
    ): AppResult<Session.SignedIn>

    /**
     * Attaches Google to the current anonymous user, keeping its uid and so everything stored under it.
     * A Google account that already has a uid of its own fails with `AppError.Conflict`.
     */
    suspend fun linkWithGoogle(
        idToken: GoogleIdToken,
        accessToken: GoogleAccessToken? = null,
    ): AppResult<Session.SignedIn>

    suspend fun signOut(): AppResult<Unit>
}
