package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Where a recipe came from. [type] discriminates the domain hierarchy; the three agent fields are
 * absent on a catalogue document and required on a generated one.
 */
@Serializable
data class RecipeSourceDto(
    val type: String? = null,
    val agentId: String? = null,
    val modelId: String? = null,
    val generatedAtMillis: Long? = null,
)
