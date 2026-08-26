package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * A user's holding of one [Ingredient] — or, when the catalogue has never heard of it, its own
 * [freeText] — and how much there is, optionally where and until when.
 *
 * Exactly one of [ingredient] and [freeText] is set, the same invariant [ShoppingItem] enforces
 * in its own factory. A free-text holding matches no recipe line: see [PantryMatcher].
 *
 * [location] is a [TermRef] because where food is stored is a taxonomy, not an enum.
 */
data class PantryItem(
    val id: PantryItemId,
    val ingredient: IngredientId?,
    val freeText: String?,
    val quantity: Quantity,
    val location: TermRef?,
    val expiresAt: Instant?,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * Builds a holding, rejecting the both-null and both-non-null cases for [ingredient]
         * and [freeText] — see [ShoppingItem.create] for the same rule stated once already.
         */
        fun create(
            id: PantryItemId,
            quantity: Quantity,
            updatedAt: Instant,
            ingredient: IngredientId? = null,
            freeText: String? = null,
            location: TermRef? = null,
            expiresAt: Instant? = null,
        ): AppResult<PantryItem> {
            val text = freeText?.takeIf { it.isNotBlank() }
            if ((ingredient == null) == (text == null)) {
                return AppResult.Failure(
                    AppError.Validation("ingredient", "exactly one of ingredient or freeText must be set"),
                )
            }
            return AppResult.Success(PantryItem(id, ingredient, text, quantity, location, expiresAt, updatedAt))
        }
    }
}

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
