package com.kitchenai.admin.requests

data class RequestQuery(
    val text: String = "",
    /** Null shows every status. */
    val status: RequestStatus? = RequestStatus.OPEN,
)

/** Most typed first, then most recent: what is missing for the most people comes to the top. */
fun List<IngredientRequest>.matching(query: RequestQuery): List<IngredientRequest> {
    val needle = comparable(query.text.trim())
    return filter { request ->
        (query.status == null || request.status == query.status) &&
            (needle.isEmpty() || comparable(request.text).contains(needle))
    }.sortedWith(compareByDescending<IngredientRequest> { it.count }.thenByDescending { it.lastSeenMillis })
}
