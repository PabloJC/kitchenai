package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.UserId
import dev.gitlive.firebase.auth.FirebaseUser

/**
 * Keyed on primitives rather than on [FirebaseUser]: that type cannot be built off a device,
 * so peeling it away here is what makes the mapping testable at all.
 */
internal fun sessionOf(
    uid: String?,
    isAnonymous: Boolean,
): Session =
    when (val userId = uid?.let(UserId::of)) {
        is AppResult.Success -> Session.SignedIn(userId.data, isAnonymous)
        // A null user is a signed-out one; a blank uid is a Firebase contract the app cannot
        // honour, and treating it as signed out is the only safe reading of it.
        else -> Session.SignedOut
    }

internal fun FirebaseUser?.toSession(): Session = if (this == null) Session.SignedOut else sessionOf(uid, isAnonymous)
