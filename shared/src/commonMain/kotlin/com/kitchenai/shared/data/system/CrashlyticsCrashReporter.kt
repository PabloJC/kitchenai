package com.kitchenai.shared.data.system

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.port.CrashReporter
import dev.gitlive.firebase.crashlytics.FirebaseCrashlytics

/**
 * [CrashReporter] on Crashlytics. The error's cause is never attached: its message and its chain
 * are the SDK's, and may quote the request. Whether anything leaves the device is the build's
 * collection flag (on in release, off in debug), not this class's.
 */
internal class CrashlyticsCrashReporter(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {
    override fun recordNonFatal(
        operation: String,
        error: AppError,
    ) {
        val summary = nonFatalSummary(operation, error) ?: return
        crashlytics.recordException(NonFatalError(summary))
    }
}

private class NonFatalError(
    summary: String,
) : Exception(summary)
