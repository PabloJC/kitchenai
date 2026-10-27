package com.kitchenai.ui.presentation.reminder

/** [offering] is the one-time question, raised once the pantry holds something with a date. */
data class ExpiryReminderUiState(
    val offering: Boolean = false,
)
