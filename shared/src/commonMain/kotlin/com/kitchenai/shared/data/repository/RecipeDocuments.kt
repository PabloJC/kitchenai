package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.RecipeDto
import kotlinx.serialization.json.Json

/**
 * Half of Firestore's ~1 MiB document cap. The headroom is deliberate: field names and the
 * encoder's own overhead are not part of what this measures.
 */
internal const val MAX_SAVED_RECIPE_BYTES = 512 * 1024

private val documentJson = Json { encodeDefaults = true }

/** UTF-8 bytes, because that is the unit Firestore charges a document in. */
internal fun RecipeDto.serialisedBytes(): Int =
    documentJson.encodeToString(RecipeDto.serializer(), this).encodeToByteArray().size

/**
 * Runs before the write. An agent that loops produces a document Firestore refuses with an opaque
 * failure, arriving long after the user asked for the recipe to be kept.
 */
internal fun RecipeDto.withinDocumentLimit(): AppResult<Unit> =
    if (serialisedBytes() > MAX_SAVED_RECIPE_BYTES) {
        AppResult.Failure(AppError.Validation("recipe", "too large"))
    } else {
        AppResult.Success(Unit)
    }

/**
 * Returns the first read that finds the document, in the order given, and [AppError.NotFound] when
 * none of them does. A read that fails stops the walk: a denied or unreachable collection is an
 * error to report, not an absence to fall through.
 *
 * The order is the point, which is why it is a pure function: a saved copy is looked up before the
 * catalogue, since a generated recipe exists nowhere but the user's own collection.
 */
internal suspend fun <T> firstFound(
    resource: String,
    reads: List<suspend () -> AppResult<T?>>,
): AppResult<T> {
    reads.forEach { read ->
        when (val found = read()) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.data?.let { return AppResult.Success(it) }
        }
    }
    return AppResult.Failure(AppError.NotFound(resource))
}
