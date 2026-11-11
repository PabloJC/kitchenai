package com.kitchenai.admin.requests

import kotlinx.coroutines.flow.Flow

/** The queue as the admin sees it. Only `status` and `ingredientId` can be changed: the counts are the function's. */
interface IngredientRequestStore {
    fun observeRequests(): Flow<Result<List<IngredientRequest>>>

    /** A null [ingredientId] removes the field, as reopening or dismissing must. */
    suspend fun setStatus(
        id: String,
        status: RequestStatus,
        ingredientId: String?,
    ): Result<Unit>
}
