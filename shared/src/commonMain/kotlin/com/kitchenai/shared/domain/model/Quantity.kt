package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult

/**
 * An amount of something, optionally expressed in a unit.
 *
 * A null [unit] means a plain count of things ("three onions"). Units are [TermRef]s rather
 * than an enum because metric, imperial and cooking units are regional.
 *
 * **The MVP performs no unit conversion.** Arithmetic between different units fails; it never
 * silently converts grams into ounces.
 */
data class Quantity(
    val amount: Double,
    val unit: TermRef? = null,
) {
    /** True when both sides carry the very same unit, null included. */
    fun canCombineWith(other: Quantity): Boolean = unit == other.unit

    operator fun plus(other: Quantity): AppResult<Quantity> = combine(other) { a, b -> a + b }

    operator fun minus(other: Quantity): AppResult<Quantity> = combine(other) { a, b -> a - b }

    private inline fun combine(
        other: Quantity,
        op: (Double, Double) -> Double,
    ): AppResult<Quantity> =
        if (canCombineWith(other)) {
            AppResult.Success(copy(amount = op(amount, other.amount)))
        } else {
            AppResult.Failure(AppError.Validation("unit", "cannot combine quantities with different units"))
        }
}
