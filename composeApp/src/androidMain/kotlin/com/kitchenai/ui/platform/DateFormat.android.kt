package com.kitchenai.ui.platform

import kotlinx.datetime.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

actual fun platformFormatDate(date: LocalDate): String =
    java.time.LocalDate
        .of(date.year, date.month.ordinal + 1, date.day)
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
