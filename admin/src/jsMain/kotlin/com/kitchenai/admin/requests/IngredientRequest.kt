package com.kitchenai.admin.requests

/** One text people typed instead of picking an ingredient, as `functions/src/ingredientRequests.ts` counts it. */
data class IngredientRequest(
    val id: String,
    val text: String,
    val count: Int,
    val activeCount: Int,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val status: RequestStatus,
    val ingredientId: String? = null,
    /** When its items were last converted (#405). */
    val migratedAtMillis: Long? = null,
)
