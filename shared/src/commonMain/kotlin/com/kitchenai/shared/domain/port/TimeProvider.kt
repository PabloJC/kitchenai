package com.kitchenai.shared.domain.port

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The clock as a port: reading the system time is a platform fact, and a use case that stamps
 * `updatedAt` is untestable without being able to freeze it.
 */
fun interface TimeProvider {
    fun now(): Instant

    /** The zone calendar days are judged in. UTC unless overridden, so a frozen test clock stays deterministic. */
    fun zone(): TimeZone = TimeZone.UTC
}
