package com.kitchenai.shared.domain.model

import kotlin.time.Instant

/**
 * One day's reminder: when it fires and what it is about. [expiring] carries the items themselves,
 * soonest first, so the device can name a few; the platform keeps them off a locked screen.
 */
data class ExpiryDigest(
    val fireAt: Instant,
    /** Still good at [fireAt] but gone within a day of it. */
    val expiring: List<PantryItem>,
    /** Already past its date at [fireAt]. */
    val expiredCount: Int,
) {
    val expiringCount: Int get() = expiring.size
}
