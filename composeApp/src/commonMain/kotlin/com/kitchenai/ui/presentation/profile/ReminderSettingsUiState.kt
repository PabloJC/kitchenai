package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.ui.presentation.common.UiText

/** The Profile's reminder rows: the stored setting, and why turning it on did not stick. */
data class ReminderSettingsUiState(
    val enabled: Boolean = false,
    val hour: Int = ReminderSettings.DEFAULT_HOUR,
    val minute: Int = 0,
    /** The system refused notifications, so the switch stayed off; the row says where to allow them. */
    val permissionRefused: Boolean = false,
    val error: UiText? = null,
)
