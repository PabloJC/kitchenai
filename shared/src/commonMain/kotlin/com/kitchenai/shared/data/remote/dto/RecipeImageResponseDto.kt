package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecipeImageResponseDto(
    val schemaVersion: Int,
    val imageUrl: String,
)
