package com.kitchenai.ui.platform

import org.koin.core.scope.Scope

/** Hands the next reminder to the system so it fires with the app closed. At most one: a new one replaces it. */
interface ExpiryReminderScheduler {
    fun schedule(reminder: ScheduledReminder)

    fun cancel()
}

/** Resolved from the Koin [Scope] like the network monitor, so the platform half stays in its source set. */
expect fun Scope.platformExpiryReminderScheduler(): ExpiryReminderScheduler
