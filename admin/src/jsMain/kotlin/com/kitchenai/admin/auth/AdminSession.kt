package com.kitchenai.admin.auth

import com.kitchenai.admin.core.catchingFailures

/** Where the signed-in account stands; only [Admin] may render anything that reads Firestore. */
sealed interface AdminSession {
    data object Loading : AdminSession

    data object SignedOut : AdminSession

    data class NotAdmin(
        val email: String?,
    ) : AdminSession

    data class Admin(
        val email: String?,
    ) : AdminSession
}

/** The session for an account and the claims of its current ID token. */
fun sessionFor(
    email: String?,
    claims: Map<String, Any?>,
): AdminSession = if (isAdminClaims(claims)) AdminSession.Admin(email) else AdminSession.NotAdmin(email)

/** Fails closed: claims that cannot be read (offline, revoked token) never leave the shell mounted. */
suspend fun sessionFor(
    email: String?,
    readClaims: suspend () -> Map<String, Any?>,
): AdminSession =
    catchingFailures(readClaims).fold(
        onSuccess = { claims -> sessionFor(email, claims) },
        onFailure = { AdminSession.SignedOut },
    )
