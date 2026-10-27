package com.kitchenai.shared.domain.model

import kotlin.time.Instant

/** One day's reminder: when it fires and what it is about. Counts only: a notification is no place for names. */
data class ExpiryDigest(
    val fireAt: Instant,
    /** Still good at [fireAt] but gone within a day of it. */
    val expiringCount: Int,
    /** Already past its date at [fireAt]. */
    val expiredCount: Int,
)
