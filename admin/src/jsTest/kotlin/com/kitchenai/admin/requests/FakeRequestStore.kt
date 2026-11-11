package com.kitchenai.admin.requests

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The queue in memory; [refusal] makes every status write fail with that message. */
class FakeRequestStore(
    vararg seed: IngredientRequest,
) {
    val requests = MutableStateFlow(seed.toList())
    var refusal: String? = null

    val store =
        object : IngredientRequestStore {
            override fun observeRequests(): Flow<Result<List<IngredientRequest>>> = requests.map { Result.success(it) }

            override suspend fun setStatus(
                id: String,
                status: RequestStatus,
                ingredientId: String?,
            ): Result<Unit> {
                refusal?.let { return Result.failure(IllegalStateException(it)) }
                requests.value =
                    requests.value.map { request ->
                        if (request.id == id) request.copy(status = status, ingredientId = ingredientId) else request
                    }
                return Result.success(Unit)
            }
        }

    fun status(id: String): IngredientRequest = requests.value.single { it.id == id }
}

fun request(
    id: String,
    text: String,
    count: Int = 1,
    lastSeenMillis: Long = 0,
    status: RequestStatus = RequestStatus.OPEN,
    ingredientId: String? = null,
): IngredientRequest = IngredientRequest(id, text, count, count, 0, lastSeenMillis, status, ingredientId)
