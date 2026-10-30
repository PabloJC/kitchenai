package com.kitchenai.ui.platform

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.picker_cancel
import com.kitchenai.ui.resources.picker_done
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** Material's picker works in UTC-midnight millis, so the day goes in and comes out through UTC. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformDatePicker(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker =
        rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { millis ->
                    onPick(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                } ?: onDismiss()
            }) { Text(stringResource(Res.string.picker_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.picker_cancel)) } },
    ) { DatePicker(state = picker) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PlatformTimePicker(
    hour: Int,
    minute: Int,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = platformIs24HourClock())
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onPick(picker.hour, picker.minute) },
            ) { Text(stringResource(Res.string.picker_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.picker_cancel)) } },
        text = { TimePicker(state = picker) },
    )
}
