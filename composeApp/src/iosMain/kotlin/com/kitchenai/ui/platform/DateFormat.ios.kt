package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT

/** Formatted in UTC from UTC midnight, so no device offset can move it to a neighbouring day. */
actual fun platformFormatDate(date: LocalDate): String {
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterMediumStyle
    formatter.timeStyle = NSDateFormatterNoStyle
    formatter.timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
    val seconds = date.atStartOfDayIn(TimeZone.UTC).epochSeconds
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(seconds.toDouble()))
}

/** The short time style follows both the locale and the device's own 24-Hour Time switch. */
@Composable
actual fun platformFormatTime(
    hour: Int,
    minute: Int,
): String =
    remember(hour, minute) {
        val formatter = NSDateFormatter()
        formatter.dateStyle = NSDateFormatterNoStyle
        formatter.timeStyle = NSDateFormatterShortStyle
        formatter.stringFromDate(todayAt(hour, minute))
    }

/** "j" is the locale's preferred hour: its pattern carries an "a" (AM/PM) only on a 12-hour clock. */
@Composable
actual fun platformIs24HourClock(): Boolean =
    remember {
        NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains('a') != true
    }

/** Today at [hour]:[minute] on the device's calendar and zone, which is how a time of day is shown and picked. */
internal fun todayAt(
    hour: Int,
    minute: Int,
): NSDate =
    NSCalendar.currentCalendar.dateBySettingHour(
        hour.toLong(),
        minute = minute.toLong(),
        second = 0,
        ofDate = NSDate(),
        options = 0u,
    ) ?: NSDate()
