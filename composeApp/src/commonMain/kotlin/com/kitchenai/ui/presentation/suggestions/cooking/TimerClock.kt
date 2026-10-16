package com.kitchenai.ui.presentation.suggestions.cooking

import kotlin.time.Duration

/** "9:05", or "1:02:05" past an hour; seconds round up, so a timer never reads 0:00 while it still runs. */
fun Duration.toClock(): String {
    val total = (inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = (total % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
