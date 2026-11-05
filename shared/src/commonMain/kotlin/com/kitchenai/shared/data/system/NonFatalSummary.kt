package com.kitchenai.shared.data.system

import com.kitchenai.shared.core.AppError
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.code

/**
 * The whole of a non-fatal report: the operation, the error's kind and the SDK status code.
 * Never a message or a field of the error: an SDK message can quote the request, which is user data.
 * Null for [AppError.Network]: being offline is the user's connection, not a defect.
 */
internal fun nonFatalSummary(
    operation: String,
    error: AppError,
): String? {
    val kind = error.kind() ?: return null
    val code = error.cause?.statusCode()
    return if (code == null) "$operation: $kind" else "$operation: $kind ($code)"
}

// Literal names rather than `simpleName`: R8 renames classes in the release build, and messages are not deobfuscated.
private fun AppError.kind(): String? =
    when (this) {
        is AppError.Network -> null
        // The quota doing its job, not a fault worth a report.
        is AppError.DailyLimit -> null
        is AppError.Timeout -> "Timeout"
        is AppError.Unauthorized -> "Unauthorized"
        is AppError.NotFound -> "NotFound"
        is AppError.Validation -> "Validation"
        is AppError.Conflict -> "Conflict"
        is AppError.Unknown -> "Unknown"
    }

private fun Throwable.statusCode(): String? =
    when (this) {
        is FirebaseFirestoreException -> code.name
        is FirebaseFunctionsException -> code.name
        else -> null
    }
