package com.kitchenai.shared.domain.model

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.service.UnitConverter
import kotlin.math.abs
import kotlin.math.ceil

/**
 * An amount of something, optionally expressed in a unit.
 *
 * A null [unit] means a plain count of things ("three onions"). Units are [TermRef]s rather
 * than an enum because metric, imperial and cooking units are regional.
 *
 * The operators on their own never convert: arithmetic between different units fails. Passing a
 * [UnitConverter] converts the right-hand side into the left-hand unit when both are convertible
 * units of one dimension, and still fails for everything else.
 */
data class Quantity(
    val amount: Double,
    val unit: TermRef? = null,
) {
    /** True when both sides carry the very same unit, null included. */
    fun canCombineWith(other: Quantity): Boolean = unit == other.unit

    /** True when [other] is in this unit, or in one [units] can convert into it. */
    fun canCombineWith(
        other: Quantity,
        units: UnitConverter,
    ): Boolean = units.amountIn(other, unit) != null

    operator fun plus(other: Quantity): AppResult<Quantity> = combine(other, UnitConverter.NONE) { a, b -> a + b }

    operator fun minus(other: Quantity): AppResult<Quantity> = combine(other, UnitConverter.NONE) { a, b -> a - b }

    /** The result stays in this quantity's unit, whatever [other] was written in. */
    fun plus(
        other: Quantity,
        units: UnitConverter,
    ): AppResult<Quantity> = combine(other, units) { a, b -> a + b }

    fun minus(
        other: Quantity,
        units: UnitConverter,
    ): AppResult<Quantity> = combine(other, units) { a, b -> a - b }

    private inline fun combine(
        other: Quantity,
        units: UnitConverter,
        op: (Double, Double) -> Double,
    ): AppResult<Quantity> {
        if (canCombineWith(other)) return AppResult.Success(copy(amount = op(amount, other.amount)))
        val converted = units.amountIn(other, unit)
        return if (converted == null) {
            AppResult.Failure(AppError.Validation("unit", "cannot combine quantities with different units"))
        } else {
            AppResult.Success(copy(amount = settled(op(amount, converted), amount, converted)))
        }
    }
}

/** What a unit conversion can leave behind: 0.1 kg - 100 g must be exactly zero, not -1e-17. */
private const val AMOUNT_TOLERANCE = 1e-9

private fun settled(
    result: Double,
    left: Double,
    right: Double,
): Double = if (abs(result) <= AMOUNT_TOLERANCE * maxOf(abs(left), abs(right))) 0.0 else result

/** True when this amount reaches [required], ignoring the rounding a unit conversion leaves. */
internal fun Double.reaches(required: Double): Boolean = this >= required - AMOUNT_TOLERANCE * maxOf(1.0, abs(required))

/** The next whole number up, except that a hair above a whole number is that number. */
internal fun Double.ceilToWhole(): Double =
    if (this <= 0.0) this else ceil(this - AMOUNT_TOLERANCE * maxOf(1.0, this)).coerceAtLeast(1.0)
