package com.kitchenai.ui.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.PlatformTimePicker
import com.kitchenai.ui.platform.platformFormatTime
import com.kitchenai.ui.platform.rememberNotificationPermissionRequest
import com.kitchenai.ui.platform.rememberOpenNotificationSettings
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.reminder_open_settings
import com.kitchenai.ui.resources.reminder_setting_body
import com.kitchenai.ui.resources.reminder_setting_refused
import com.kitchenai.ui.resources.reminder_setting_time
import com.kitchenai.ui.resources.reminder_setting_title
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
            val time = platformFormatTime(state.hour, state.minute)
            ListItem(
                headlineContent = { Text(stringResource(Res.string.reminder_setting_time)) },
                trailingContent = { Text(time) },
                modifier =
                    Modifier
                        .clickable(role = Role.Button) { choosingTime = true }
                        .semantics { stateDescription = time },
            )
        }
        if (state.permissionRefused) RefusedNote(onCameBack = { onToggle(true) })
        state.error?.let { message -> Note(message.resolve()) }
    }
    if (choosingTime) {
        PlatformTimePicker(
            hour = state.hour,
            minute = state.minute,
            onPick = { hour, minute ->
                viewModel.setTime(hour, minute)
                choosingTime = false
            },
            onDismiss = { choosingTime = false },
        )
    }
}

/**
 * The way out of a refusal: the app's own notification settings. Coming back asks again, which
 * the system answers at once now that the choice is made, so allowing them turns this note off.
 */
@Composable
private fun RefusedNote(onCameBack: () -> Unit) {
    val openSettings = rememberOpenNotificationSettings()
    var opened by remember { mutableStateOf(false) }
    // Keyed on nothing: a key change would run this at once, before the person has even left.
    LifecycleResumeEffect(Unit) {
        if (opened) {
            opened = false
            onCameBack()
        }
        onPauseOrDispose {}
    }
    Note(stringResource(Res.string.reminder_setting_refused))
    TextButton(
        onClick = {
            opened = true
            openSettings()
        },
        modifier = Modifier.padding(horizontal = Dimens.small),
    ) { Text(stringResource(Res.string.reminder_open_settings)) }
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
