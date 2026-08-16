package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.local.RecipeDao
import com.kitchenai.shared.data.local.RecipeEntity
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.port.RecipeCachePort
import com.kitchenai.shared.domain.port.TimeProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private val payloadJson = Json { encodeDefaults = true }

/**
 * [RecipeCachePort] over Room. Reuses the Firestore mapper's [Recipe] to [RecipeDto] conversion —
 * the shape a recipe travels in does not depend on where it is going — and folds the resulting
 * DTO into [RecipeEntity.payload] as one JSON blob rather than a table per nested list.
 */
class RoomRecipeCacheRepository(
    private val dao: RecipeDao,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : RecipeCachePort {
    override suspend fun getAll(): AppResult<List<Recipe>> =
        withContext(dispatchers.io) {
            AppResult.Success(dao.getAll().map { it.toRecipe() }.decodedOrDropped())
        }

    override suspend fun replaceAll(recipes: List<Recipe>): AppResult<Unit> =
        withContext(dispatchers.io) {
            dao.replaceAll(recipes.map { it.toEntity(time.now()) })
            AppResult.Success(Unit)
        }

    private fun Recipe.toEntity(savedAt: Instant): RecipeEntity {
        val dto = toDto(savedAt)
        return RecipeEntity(
            id = id.value,
            title = title,
            source = dto.source?.type.orEmpty(),
            savedAtMillis = dto.savedAtMillis ?: savedAt.toEpochMilliseconds(),
            payload = payloadJson.encodeToString(RecipeDto.serializer(), dto),
        )
    }

    private fun RecipeEntity.toRecipe(): AppResult<Recipe> =
        runCatching { payloadJson.decodeFromString(RecipeDto.serializer(), payload) }
            .fold(
                onSuccess = { dto -> dto.toDomain(id) },
                onFailure = { failure -> AppResult.Failure(AppError.Unknown(failure)) },
            )
}
