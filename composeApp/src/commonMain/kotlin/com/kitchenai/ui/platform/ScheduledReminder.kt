package com.kitchenai.ui.platform

import kotlin.time.Instant

/** A reminder already worded: the system shows it later, with the app closed and its resources out of reach. */
data class ScheduledReminder(
    val fireAt: Instant,
    val title: String,
    val body: String,
    /** Counts only, for a locked screen. */
    val publicBody: String,
    /** The name Android lists the reminders under in its notification settings; iOS has no equivalent. */
    val channelName: String,
)
