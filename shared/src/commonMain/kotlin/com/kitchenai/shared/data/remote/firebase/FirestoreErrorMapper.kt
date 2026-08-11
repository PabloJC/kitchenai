package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.code
import kotlin.coroutines.cancellation.CancellationException

/**
 * The only translation from a Firestore failure into the error vocabulary of the app.
 * Rethrows [CancellationException]: mapping it would swallow a cancellation and break
 * structured concurrency.
 */
fun Throwable.toAppError(): AppError {
    if (this is CancellationException) throw this

    return when (this) {
        is FirebaseFirestoreException -> appErrorForCode(code.name, this)
        else -> AppError.Unknown(this)
    }
}

/**
 * Keyed on the code name rather than on `FirestoreExceptionCode`: on Android that type is a
 * typealias to the SDK enum, whose static initialiser needs the Android runtime and therefore
 * cannot be loaded by a host test. Every branch below is covered by name in the test.
 */
internal fun appErrorForCode(
    code: String,
    cause: Throwable?,
): AppError =
    when (code) {
        "PERMISSION_DENIED", "UNAUTHENTICATED" -> AppError.Unauthorized(cause)
        "UNAVAILABLE", "DEADLINE_EXCEEDED" -> AppError.Network(cause)
        "NOT_FOUND" -> AppError.NotFound(FIRESTORE_RESOURCE)
        else -> AppError.Unknown(cause)
    }

/** The SDK error carries no path, so the resource is the store itself. */
internal const val FIRESTORE_RESOURCE = "firestore"
