package com.kitchenai.shared.domain.model

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * A user's holding of one [Ingredient]: how much there is, optionally where and until when.
 *
 * [location] is a [TermRef] because where food is stored is a taxonomy, not an enum.
 */
data class PantryItem(
    val id: PantryItemId,
    val ingredient: IngredientId,
    val quantity: Quantity,
    val location: TermRef?,
    val expiresAt: Instant?,
    val updatedAt: Instant,
)

/**
 * Classifies this holding against [now].
 *
 * [expiringSoonWindow] is a parameter and not a constant: how many days count as soon is a
 * product setting, and hardcoding it here is the same mistake as hardcoding a diet.
 */
fun PantryItem.freshnessAt(
    now: Instant,
    expiringSoonWindow: Duration,
): Freshness {
    val expiry = expiresAt ?: return Freshness.Unknown
    val remaining = expiry - now
    return when {
        remaining <= Duration.ZERO -> Freshness.Expired
        remaining <= expiringSoonWindow -> Freshness.ExpiringSoon(remaining.daysLeft())
        else -> Freshness.Fresh
    }
}

/** Rounded up: half a day of shelf life left is still a day, and zero would read as expired. */
private fun Duration.daysLeft(): Int = ceil(toDouble(DurationUnit.DAYS)).toInt()
