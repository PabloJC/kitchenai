package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/** The only place in the app that knows Firebase Auth exists. */
class FirebaseSessionAdapter(
    private val auth: FirebaseAuth,
    private val dispatchers: DispatcherProvider,
) : SessionRepositoryContract {
    // A link changes isAnonymous without changing the user, and authStateChanged only fires on a user change.
    private val linked = MutableSharedFlow<Session>(extraBufferCapacity = 1)

    /**
     * A restored user is readable synchronously, so emitting it up front removes the
     * signed-out flash while the listener registers. A null `currentUser` is deliberately not
     * emitted: on a cold start it cannot be told apart from "not restored yet", and the first
     * `SignedOut` must come from the listener, which only speaks once restoration is done.
     */
    override fun observeSession(): Flow<Session> =
        merge(auth.authStateChanged.map { user -> user.toSession() }, linked)
            .onStart { auth.currentUser?.let { user -> emit(user.toSession()) } }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    /**
     * Firebase returns the user already signed in rather than minting a second one, so calling
     * this twice cannot produce a second anonymous uid.
     */
    override suspend fun signInAnonymously(): AppResult<Session.SignedIn> {
        val signedIn = firestoreCall(dispatchers) { auth.signInAnonymously().user }

        return when (signedIn) {
            is AppResult.Failure -> signedIn
            is AppResult.Success ->
                when (val session = signedIn.data.toSession()) {
                    is Session.SignedIn -> AppResult.Success(session)
                    Session.SignedOut -> AppResult.Failure(AppError.Unknown(MISSING_USER))
                }
        }
    }

    /** A sign-in, not a link: an anonymous session is swapped for the Google one and its data left behind (#250). */
    override suspend fun signInWithGoogle(
        idToken: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> {
        val signedIn =
            firestoreCall(dispatchers) {
                auth.signInWithCredential(GoogleAuthProvider.credential(idToken.value, accessToken?.value)).user
            }

        return when (signedIn) {
            is AppResult.Failure -> signedIn
            is AppResult.Success ->
                when (val session = signedIn.data.toSession()) {
                    is Session.SignedIn -> AppResult.Success(session)
                    Session.SignedOut -> AppResult.Failure(AppError.Unknown(MISSING_USER))
                }
        }
    }

    override suspend fun linkWithGoogle(
        idToken: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> {
        val linkedUser =
            firestoreCall(dispatchers) {
                val user = auth.currentUser ?: throw MISSING_USER
                user.linkWithCredential(GoogleAuthProvider.credential(idToken.value, accessToken?.value)).user
            }

        return when (linkedUser) {
            is AppResult.Failure -> linkedUser
            is AppResult.Success ->
                when (val session = linkedUser.data.toSession()) {
                    is Session.SignedIn -> AppResult.Success(session).also { linked.tryEmit(session) }
                    Session.SignedOut -> AppResult.Failure(AppError.Unknown(MISSING_USER))
                }
        }
    }

    override suspend fun signOut(): AppResult<Unit> = firestoreCall(dispatchers) { auth.signOut() }

    private companion object {
        // Asserting non-null here would trade a handled failure for a crash.
        val MISSING_USER = IllegalStateException("Sign-in returned no user")
    }
}
