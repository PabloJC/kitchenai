package com.kitchenai.shared.domain.service

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UnitConversion

/**
 * Converts between units of one dimension using the [UnitConversion] each unit term carries, so a
 * regional unit is catalogue data, not a release. Mass to volume would need a density and a count
 * never converts: both are rejected. [NONE] converts nothing, as before this existed.
 */
class UnitConverter(units: List<Term>) {
    private val known: Set<TermRef> = units.mapTo(HashSet()) { it.ref }

    // A factor that is not a positive finite number would turn every conversion into garbage.
    private val conversions: Map<TermRef, UnitConversion> =
        units
            .mapNotNull { term -> term.conversion?.takeIf { it.isUsable() }?.let { term.ref to it } }
            .toMap()

    /** [quantity] expressed in [to]; the amount is exact only up to floating-point rounding. */
    fun convert(
        quantity: Quantity,
        to: TermRef,
    ): AppResult<Quantity> {
        val from = quantity.unit
        val source = from?.let(conversions::get)
        val target = conversions[to]
        return when {
            from == null -> rejected("a count has no unit to convert")
            source == null || target == null ->
                rejected(if (from in known && to in known) "unit has no conversion data" else "unknown unit")
            source.dimension != target.dimension -> rejected("units measure different things")
            else -> AppResult.Success(Quantity(quantity.amount * source.factor / target.factor, to))
        }
    }

    /** The amount of [quantity] in [unit], or null when the two cannot be compared at all. */
    fun amountIn(
        quantity: Quantity,
        unit: TermRef?,
    ): Double? =
        when {
            quantity.unit == unit -> quantity.amount
            unit == null -> null
            else -> (convert(quantity, unit) as? AppResult.Success)?.data?.amount
        }

    private fun rejected(reason: String): AppResult.Failure = AppResult.Failure(AppError.Validation("unit", reason))

    companion object {
        /** Converts nothing: every unit is only ever equal to itself. */
        val NONE: UnitConverter = UnitConverter(emptyList())
    }
}

private fun UnitConversion.isUsable(): Boolean = factor.isFinite() && factor > 0.0
