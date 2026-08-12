package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * One entry of `constraints` in `users/{uid}`. The term is stored as its two identifiers rather
 * than as a nested object: flat fields keep partial updates and index definitions simple.
 */
@Serializable
data class DietaryConstraintDto(
    val taxonomy: String? = null,
    val term: String? = null,
    val strength: String? = null,
)
