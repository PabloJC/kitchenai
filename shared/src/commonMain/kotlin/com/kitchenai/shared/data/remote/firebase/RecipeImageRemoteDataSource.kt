package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.data.remote.dto.RecipeImageRequestDto
import com.kitchenai.shared.data.remote.dto.RecipeImageResponseDto
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlin.time.Duration.Companion.seconds

private const val RECIPE_IMAGE = "recipeImage"

/** Above the function's own 60 s, so the function gives up first and says why. */
private val CALL_TIMEOUT = 90.seconds

internal const val IMAGE_SCHEMA_VERSION = 1

/** Wraps the `recipeImage` callable: a server-issued key in, the photograph's address out. */
class RecipeImageRemoteDataSource(
    private val functions: FirebaseFunctions,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun imageUrl(imageKey: String): AppResult<String> =
        firestoreCall(dispatchers) {
            functions
                .httpsCallable(RECIPE_IMAGE, CALL_TIMEOUT)
                .invoke(RecipeImageRequestDto.serializer(), RecipeImageRequestDto(IMAGE_SCHEMA_VERSION, imageKey))
                .data(RecipeImageResponseDto.serializer())
        }.flatMap { it.imageUrlOrError() }
}

/** The address reaches an image loader, so anything that is not a web address stops here. */
internal fun RecipeImageResponseDto.imageUrlOrError(): AppResult<String> =
    when {
        schemaVersion != IMAGE_SCHEMA_VERSION ->
            AppResult.Failure(AppError.Validation("schemaVersion", "unsupported response schema"))
        !imageUrl.startsWith("https://") && !imageUrl.startsWith("http://") ->
            AppResult.Failure(AppError.Validation("imageUrl", "is not a web address"))
        else -> AppResult.Success(imageUrl)
    }
