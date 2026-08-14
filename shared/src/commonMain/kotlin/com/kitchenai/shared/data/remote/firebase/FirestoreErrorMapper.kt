package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.code
import kotlin.coroutines.cancellation.CancellationException

/**
 * The only translation from a Firebase failure into the error vocabulary of the app.
 * Rethrows [CancellationException]: mapping it would swallow a cancellation and break
 * structured concurrency.
 *
 * Both SDKs are handled here because both use the same gRPC status names, and a caller that
 * matched only one would report a refused call as [AppError.Unknown] — which is what a user
 * sees as "something went wrong" instead of "you are not allowed to do that".
 */
fun Throwable.toAppError(): AppError {
    if (this is CancellationException) throw this

    return when (this) {
        is FirebaseFirestoreException -> appErrorForCode(code.name, this)
        is FirebaseFunctionsException -> appErrorForCode(code.name, this, FUNCTIONS_RESOURCE)
        else -> AppError.Unknown(this)
    }
}

/**
 * Keyed on the code name rather than on the SDKs' code enums: on Android those are typealiases
 * to the Firebase enums, whose static initialisers need the Android runtime and cannot be loaded
 * by a host test. Every branch below is covered by name in the test.
 *
 * `RESOURCE_EXHAUSTED` deliberately falls through to [AppError.Unknown]. #51 asked for
 * [AppError.Network] because it is retryable, but every screen renders that as "No connection",
 * and a rate-limited user has a connection. "Something went wrong" is the true statement.
 *
 * `DEADLINE_EXCEEDED` was mapped to [AppError.Network] for the same reason and with the same
 * fault: a call that timed out reached the network. It has its own case now, because unlike
 * `RESOURCE_EXHAUSTED` there is a useful thing to tell the user, and it is "try again".
 */
internal fun appErrorForCode(
    code: String,
    cause: Throwable?,
    resource: String = FIRESTORE_RESOURCE,
): AppError =
    when (code) {
        "PERMISSION_DENIED", "UNAUTHENTICATED" -> AppError.Unauthorized(cause)
        "UNAVAILABLE" -> AppError.Network(cause)
        "DEADLINE_EXCEEDED" -> AppError.Timeout(cause)
        "NOT_FOUND" -> AppError.NotFound(resource)
        else -> AppError.Unknown(cause)
    }

/** The SDK error carries no path, so the resource is the store itself. */
internal const val FIRESTORE_RESOURCE = "firestore"

/**
 * A callable answering NOT_FOUND means the function is not deployed, which is a different
 * problem from a missing document and must not be reported as one.
 */
internal const val FUNCTIONS_RESOURCE = "function"
