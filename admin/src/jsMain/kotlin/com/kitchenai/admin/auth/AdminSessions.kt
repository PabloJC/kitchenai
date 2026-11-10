package com.kitchenai.admin.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Follows the ID token, not just the user, so a claim granted or revoked shows on the next refresh. */
fun adminSessions(): Flow<AdminSession> =
    Firebase.auth.idTokenChanged.map { user ->
        if (user == null) {
            AdminSession.SignedOut
        } else {
            sessionFor(user.email) { user.getIdTokenResult(forceRefresh = false).claims }
        }
    }
