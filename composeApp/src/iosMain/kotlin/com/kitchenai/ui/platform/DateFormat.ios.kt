package com.kitchenai.ui.platform

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSTimeZone
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
