package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError

/**
 * Failures worth a look in the crash console that did not crash the app. Takes what was being
 * done and how it failed, never the data it was done to: reports leave the device.
 */
fun interface CrashReporter {
    fun recordNonFatal(
        operation: String,
        error: AppError,
    )
}
