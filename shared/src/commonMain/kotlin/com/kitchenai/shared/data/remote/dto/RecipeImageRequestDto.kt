package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/** The whole request: a key the server issued, so no text from this device reaches a model. */
@Serializable
data class RecipeImageRequestDto(
    val schemaVersion: Int,
    val imageKey: String,
)
