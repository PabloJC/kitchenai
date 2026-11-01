package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.model.ReminderSettings

/**
 * [offering] is the one-time question, raised once the pantry holds something with a date. The
 * time it names stays a number here: the screen formats it with the device's own 12/24-hour clock.
 */
data class ExpiryReminderUiState(
    val offering: Boolean = false,
    /** As stored: Profile may have set one before the question came up. */
    val hour: Int = ReminderSettings.DEFAULT_HOUR,
    val minute: Int = 0,
)
