package com.kitchenai.ui.platform

import org.koin.core.scope.Scope

/** Hands a cooking timer to the system, so it rings with the screen locked or the app in the background. */
interface CookingTimerScheduler {
    /** Replaces any pending alarm with the same [CookingAlarm.id]. */
    fun schedule(alarm: CookingAlarm)

    /** Cancels the pending alarm and takes down its notification if it already rang. */
    fun cancel(id: String)
}

/** Resolved from the Koin [Scope] like the reminder scheduler, so the platform half stays in its source set. */
expect fun Scope.platformCookingTimerScheduler(): CookingTimerScheduler

/** Every cooking timer's alarm id starts with this; iOS presents its own in the foreground by it. */
const val COOKING_ALARM_ID_PREFIX = "cooking-timer-"
