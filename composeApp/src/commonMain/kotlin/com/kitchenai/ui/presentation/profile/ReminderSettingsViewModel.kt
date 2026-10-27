package com.kitchenai.ui.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.usecase.reminder.ObserveReminderSettingsUseCase
import com.kitchenai.shared.domain.usecase.reminder.SaveReminderSettingsUseCase
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_action
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The reminder switch and time in Profile. Every change counts as the one-time question answered,
 * so the in-context offer never asks again about something already decided here.
 */
class ReminderSettingsViewModel(
    private val observe: ObserveReminderSettingsUseCase,
    private val save: SaveReminderSettingsUseCase,
) : ViewModel() {
    private val refused = MutableStateFlow(false)
    private val failure = MutableStateFlow<UiText?>(null)

    val state: StateFlow<ReminderSettingsUiState> =
        combine(observe(), refused, failure) { settings, wasRefused, error ->
            ReminderSettingsUiState(
                enabled = settings.enabled && settings.offered,
                hour = settings.hour,
                minute = settings.minute,
                permissionRefused = wasRefused,
                error = error,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettingsUiState())

    /** [granted] is the system's answer when turning on; turning off needs none. */
    fun setEnabled(
        on: Boolean,
        granted: Boolean = true,
    ) {
        refused.value = on && !granted
        store { it.copy(enabled = on && granted, offered = true) }
    }

    fun setTime(
        hour: Int,
        minute: Int,
    ) = store { it.copy(hour = hour, minute = minute, offered = true) }

    private fun store(change: (ReminderSettings) -> ReminderSettings) {
        failure.value = null
        viewModelScope.launch {
            val result = save(change(observe().first()))
            if (result is AppResult.Failure) failure.value = result.error.describe(Res.string.error_unauthorized_action)
        }
    }
}
