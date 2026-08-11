package com.kitchenai.shared.domain.model

/**
 * How close a holding is to being unusable.
 *
 * [Unknown] is not a failure: most of the pantry carries no expiry date and never will.
 */
sealed interface Freshness {
    data object Fresh : Freshness

    data class ExpiringSoon(val daysLeft: Int) : Freshness

    data object Expired : Freshness

    data object Unknown : Freshness
}
