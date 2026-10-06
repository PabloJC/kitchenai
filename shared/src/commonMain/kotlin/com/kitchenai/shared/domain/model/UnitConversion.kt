package com.kitchenai.shared.domain.model

/**
 * How a unit term relates to the base unit of its [dimension]: `amount * factor` is the amount
 * in that base unit. The catalogue picks the base by giving it a factor of one.
 */
data class UnitConversion(
    val dimension: UnitDimension,
    val factor: Double,
)
