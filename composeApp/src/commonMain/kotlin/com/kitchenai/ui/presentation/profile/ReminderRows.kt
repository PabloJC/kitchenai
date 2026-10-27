package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.rememberNotificationPermissionRequest
import com.kitchenai.ui.presentation.common.clockTime
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.reminder_setting_body
import com.kitchenai.ui.resources.reminder_setting_refused
import com.kitchenai.ui.resources.reminder_setting_time
import com.kitchenai.ui.resources.reminder_setting_title
import com.kitchenai.ui.resources.reminder_time_cancel
import com.kitchenai.ui.resources.reminder_time_ok
import org.jetbrains.compose.resources.stringResource

/** The on/off switch, and the time once it is on. Turning it on asks the system first. */
@Composable
internal fun ReminderRows(
    state: ReminderSettingsUiState,
    viewModel: ReminderSettingsViewModel,
) {
    val requestPermission = rememberNotificationPermissionRequest()
    var choosingTime by remember { mutableStateOf(false) }
    val onToggle: (Boolean) -> Unit = { on ->
        if (on) requestPermission { granted -> viewModel.setEnabled(true, granted) } else viewModel.setEnabled(false)
    }
    Column {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.reminder_setting_title)) },
            supportingContent = { Text(stringResource(Res.string.reminder_setting_body)) },
            trailingContent = { Switch(checked = state.enabled, onCheckedChange = null) },
            modifier = Modifier.clickable(role = Role.Switch) { onToggle(!state.enabled) },
        )
        if (state.enabled) {
            val time = clockTime(state.hour, state.minute)
            ListItem(
                headlineContent = { Text(stringResource(Res.string.reminder_setting_time)) },
                trailingContent = { Text(time) },
                modifier =
                    Modifier
                        .clickable(role = Role.Button) { choosingTime = true }
                        .semantics { stateDescription = time },
            )
        }
        if (state.permissionRefused) Note(stringResource(Res.string.reminder_setting_refused))
        state.error?.let { message -> Note(message.resolve()) }
    }
    if (choosingTime) {
        TimeDialog(
            hour = state.hour,
            minute = state.minute,
            onChoose = { hour, minute ->
                viewModel.setTime(hour, minute)
                choosingTime = false
            },
            onDismiss = { choosingTime = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    hour: Int,
    minute: Int,
    onChoose: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onChoose(picker.hour, picker.minute) }) {
                Text(stringResource(Res.string.reminder_time_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.reminder_time_cancel)) } },
        text = { TimePicker(state = picker) },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = Dimens.large),
    )
}
