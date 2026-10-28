package com.kitchenai.ui.presentation.reminder

/** A reminder in words, worded now because the system shows it later with the app closed. */
data class ReminderText(
    val title: String,
    /** Names up to a few items: shown once the device is unlocked. */
    val body: String,
    /** Counts only, for a locked screen, where what is in someone's fridge is nobody else's business. */
    val publicBody: String,
    val channelName: String,
)
