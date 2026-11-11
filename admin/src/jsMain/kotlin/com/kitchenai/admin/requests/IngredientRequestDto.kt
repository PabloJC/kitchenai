package com.kitchenai.admin.requests

import kotlinx.serialization.Serializable

@Serializable
data class IngredientRequestDto(
    val text: String = "",
    val count: Int = 0,
    val activeCount: Int = 0,
    val firstSeenMillis: Long = 0,
    val lastSeenMillis: Long = 0,
    val status: String = "",
    val ingredientId: String? = null,
)

/** Null for a status the rules would not have let in; such a document is left out rather than guessed at. */
fun IngredientRequestDto.toRequest(id: String): IngredientRequest? =
    RequestStatus.fromWire(status)?.let { known ->
        IngredientRequest(id, text, count, activeCount, firstSeenMillis, lastSeenMillis, known, ingredientId)
    }
