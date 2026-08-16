package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.local.RecipeEntity
import com.kitchenai.shared.data.local.RecipeLocalDataSource
import com.kitchenai.shared.data.local.localCall
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.port.RecipeRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private val payloadJson = Json { encodeDefaults = true }

/**
 * [RecipeRepositoryContract] over [RecipeLocalDataSource]. Owns the domain mapping the data
 * source does not need to know about, and reuses the Firestore mapper's [Recipe] to [RecipeDto]
 * conversion — the shape a recipe travels in does not depend on where it is going — folding the
 * resulting DTO into [RecipeEntity.payload] as one JSON blob rather than a table per nested list.
 *
 * There is only one data source behind this contract today; a `RecipeRemoteDataSource` joins here
 * — #139 — rather than by making [RecipeLocalDataSource] do double duty.
 */
class RecipeRepository(
    private val localDataSource: RecipeLocalDataSource,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : RecipeRepositoryContract {
    override suspend fun getAll(): AppResult<List<Recipe>> =
        localCall(dispatchers) { localDataSource.getAll() }
            .map { entities -> entities.map { it.toRecipe() }.decodedOrDropped() }

    override suspend fun replaceAll(recipes: List<Recipe>): AppResult<Unit> =
        localCall(dispatchers) { localDataSource.replaceAll(recipes.map { it.toEntity(time.now()) }) }

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
