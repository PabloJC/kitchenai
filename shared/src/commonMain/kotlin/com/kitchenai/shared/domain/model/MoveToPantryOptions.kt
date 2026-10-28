package com.kitchenai.shared.domain.model

import kotlin.time.Instant

/**
 * What the review sheet adds to a move. The defaults are the plain move: every line undated, and a
 * line with no amount left on the list.
 */
data class MoveToPantryOptions(
    val expiresAt: Map<ShoppingItemId, Instant> = emptyMap(),
    /** A line with no amount moves as one, in the ingredient's default unit, as the pantry editor saves it. */
    val defaultAmountForBare: Boolean = false,
    /** Amounts typed in the sheet for lines that had none, in that same default unit. */
    val bareAmounts: Map<ShoppingItemId, Double> = emptyMap(),
) {
    companion object {
        const val BARE_AMOUNT: Double = 1.0
    }
}
