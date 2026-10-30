package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

/** A calendar date in the device locale's medium style ("14 oct 2026", "Oct 14, 2026"). */
expect fun platformFormatDate(date: LocalDate): String

/** A time of day as the device shows its own: "18:00" or "6:00 PM", following the 12/24-hour setting. */
@Composable
expect fun platformFormatTime(
    hour: Int,
    minute: Int,
): String

/** Whether the device shows the 24-hour clock, for a picker that should look like its own. */
@Composable
expect fun platformIs24HourClock(): Boolean
