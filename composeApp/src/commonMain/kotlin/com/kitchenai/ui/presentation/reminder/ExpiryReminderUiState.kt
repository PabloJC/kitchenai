package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.ui.presentation.common.clockTime

/** [offering] is the one-time question, raised once the pantry holds something with a date. */
data class ExpiryReminderUiState(
    val offering: Boolean = false,
    /** The time the offer names, as stored: Profile may have set one before the question came up. */
    val time: String = clockTime(ReminderSettings.DEFAULT_HOUR, 0),
)
