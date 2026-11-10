package com.kitchenai.shared.data.local

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.domain.model.Recipe
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private val payloadJson = Json { encodeDefaults = true }

/**
 * [Recipe] to [RecipeEntity]: reuses the Firestore mapper's DTO conversion — the shape a recipe
 * travels in does not depend on where it is going — folding it into one JSON
 * [RecipeEntity.payload] column rather than a table per nested list.
 */
internal fun Recipe.toEntity(savedAt: Instant): RecipeEntity {
    val dto = toDto(savedAt)
    return RecipeEntity(
        id = id.value,
        title = title,
        source = dto.source?.type.orEmpty(),
        savedAtMillis = dto.savedAtMillis ?: savedAt.toEpochMilliseconds(),
        payload = payloadJson.encodeToString(RecipeDto.serializer(), dto),
    )
}

internal fun RecipeEntity.toRecipe(): AppResult<Recipe> =
    runCatching { payloadJson.decodeFromString(RecipeDto.serializer(), payload) }
        .fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(AppError.Unknown(failure)) },
        )
