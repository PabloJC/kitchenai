package com.kitchenai.ui.platform

import org.koin.core.scope.Scope

/** Hands the planned reminders to the system so they fire with the app closed. */
interface ExpiryReminderScheduler {
    /** Replaces every pending reminder with [reminders]. */
    fun schedule(reminders: List<ScheduledReminder>)

    fun cancel()
}

/** Resolved from the Koin [Scope] like the network monitor, so the platform half stays in its source set. */
expect fun Scope.platformExpiryReminderScheduler(): ExpiryReminderScheduler
