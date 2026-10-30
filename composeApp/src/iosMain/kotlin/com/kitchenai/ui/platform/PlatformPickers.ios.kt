package com.kitchenai.ui.platform

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.picker_cancel
import com.kitchenai.ui.resources.picker_done
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import kotlin.time.Instant

// The inline calendar's natural height on an iPhone; the wheels need less.
private val CALENDAR_HEIGHT = 360.dp
private val WHEELS_HEIGHT = 216.dp

/** The system calendar, in UTC, so the day picked is the day stored whatever the device's offset. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformDatePicker(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker =
        remember {
            UIDatePicker().apply {
                datePickerMode = UIDatePickerMode.UIDatePickerModeDate
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleInline
                timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
                initial?.let { day ->
                    val seconds = day.atStartOfDayIn(TimeZone.UTC).epochSeconds.toDouble()
                    setDate(NSDate.dateWithTimeIntervalSince1970(seconds), animated = false)
                }
            }
        }
    NativePickerDialog(
        height = CALENDAR_HEIGHT,
        picker = picker,
        onDone = {
            val millis = (picker.date.timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
            onPick(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
        },
        onDismiss = onDismiss,
    )
}

/** The system wheels, in the device's own zone and clock, so 12-hour users see AM and PM. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformTimePicker(
    hour: Int,
    minute: Int,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker =
        remember {
            UIDatePicker().apply {
                datePickerMode = UIDatePickerMode.UIDatePickerModeTime
                preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
                setDate(todayAt(hour, minute), animated = false)
            }
        }
    NativePickerDialog(
        height = WHEELS_HEIGHT,
        picker = picker,
        onDone = {
            val parts =
                NSCalendar.currentCalendar.components(
                    NSCalendarUnitHour or NSCalendarUnitMinute,
                    fromDate = picker.date,
                )
            onPick(parts.hour.toInt(), parts.minute.toInt())
        },
        onDismiss = onDismiss,
    )
}

/** The picker embedded as it is, between Cancel and Done, so VoiceOver reaches it as the system's own control. */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun NativePickerDialog(
    height: Dp,
    picker: UIDatePicker,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(Res.string.picker_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.picker_cancel)) } },
        text = { UIKitView(factory = { picker }, modifier = Modifier.fillMaxWidth().height(height)) },
    )
}

private const val MILLIS_PER_SECOND = 1000.0
