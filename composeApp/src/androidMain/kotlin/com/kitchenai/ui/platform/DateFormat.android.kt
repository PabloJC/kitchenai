package com.kitchenai.ui.platform

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

actual fun platformFormatDate(date: LocalDate): String =
    java.time.LocalDate
        .of(date.year, date.month.ordinal + 1, date.day)
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Through the context: the 12/24-hour switch is a system setting the locale alone does not carry. */
@Composable
actual fun platformFormatTime(
    hour: Int,
    minute: Int,
): String {
    val context = LocalContext.current
    return remember(context, hour, minute) {
        val pattern = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
        val skeleton = DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), pattern)
        LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(skeleton))
    }
}

@Composable
actual fun platformIs24HourClock(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}
