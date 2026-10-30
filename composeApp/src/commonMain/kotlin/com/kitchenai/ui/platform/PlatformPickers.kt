package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

/**
 * A calendar day, chosen with the platform's own picker: Material's dialog on Android, the inline
 * UIDatePicker on iOS. It only returns the day; what an expiry means stays in common code.
 */
@Composable
expect fun PlatformDatePicker(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
)

/** A time of day, on Material's dial or on iOS's wheels, each following the device's 12/24-hour clock. */
@Composable
expect fun PlatformTimePicker(
    hour: Int,
    minute: Int,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
)
