package com.kitchenai.ui.presentation.reminder

/** A reminder in words, worded now because the system shows it later with the app closed. */
data class ReminderText(
    val title: String,
    val body: String,
    val channelName: String,
)
