package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/** The household block of `users/{uid}`: numbers only, so no assumption about who cooks travels. */
@Serializable
data class HouseholdDto(
    val servings: Int? = null,
    val weeklyBudget: Double? = null,
    val defaultCookingMinutes: Int? = null,
)
