package com.kitchenai.ui.platform

import org.koin.core.scope.Scope

/** Keeps the daily expiry reminder going with the app closed. */
interface ExpiryReminderScheduler {
    /**
     * Reminders are on at [hour]:[minute]. Android runs a daily job at that time that reads the
     * pantry as it is then, and ignores [reminders]. iOS cannot run code on time, so it hands
     * [reminders], worded from the latest snapshot, to the system.
     */
    fun schedule(
        reminders: List<ScheduledReminder>,
        hour: Int,
        minute: Int,
    )

    fun cancel()
}

/** Resolved from the Koin [Scope] like the network monitor, so the platform half stays in its source set. */
expect fun Scope.platformExpiryReminderScheduler(): ExpiryReminderScheduler
