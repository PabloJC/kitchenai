package com.kitchenai.shared.domain.model

/**
 * What the household needs expressed as numbers, so no assumption about who cooks for whom
 * is baked into a type.
 */
data class HouseholdContext(
    val servings: Int,
    val weeklyBudget: Double? = null,
    val defaultCookingMinutes: Int? = null,
)
