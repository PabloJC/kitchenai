package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.remote.dto.IngredientDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.remote.firebase.firestoreCall
import com.kitchenai.shared.data.remote.firebase.reportingErrorsTo
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.port.IngredientPort
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * [IngredientPort] over the read-only `ingredients` collection. Its stream is keyed by nothing,
 * so it owns a single error sink.
 */
class FirestoreIngredientRepository(
    private val paths: FirestorePaths,
    private val dispatchers: DispatcherProvider,
) : IngredientPort {
    // Buffered so that publishing a failure never suspends the listener that is dying.
    private val errors = MutableSharedFlow<AppError>(extraBufferCapacity = 1)

    override fun observeIngredients(): Flow<List<Ingredient>> =
        paths
            .ingredients()
            .snapshots
            .map { snapshot -> snapshot.toIngredients() }
            .reportingErrorsTo(errors)

    override fun ingredientErrors(): Flow<AppError> = errors.asSharedFlow()

    override suspend fun getIngredient(id: IngredientId): AppResult<Ingredient> =
        when (val read = firestoreCall(dispatchers) { paths.ingredient(id).get() }) {
            is AppResult.Failure -> read
            is AppResult.Success -> read.data.toIngredientOrNotFound()
        }

    // Same rule as the pantry: a document that will not map is dropped, never propagated as a
    // failure for the whole catalogue.
    private fun QuerySnapshot.toIngredients(): List<Ingredient> = documents.map { it.toIngredient() }.mapped().items

    private fun DocumentSnapshot.toIngredientOrNotFound(): AppResult<Ingredient> =
        if (exists) toIngredient() else AppResult.Failure(AppError.NotFound(INGREDIENT_RESOURCE))

    private fun DocumentSnapshot.toIngredient(): AppResult<Ingredient> =
        runCatching { data(IngredientDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )

    private companion object {
        // The collection, never the identifier: an error carries no user content.
        const val INGREDIENT_RESOURCE = "ingredient"
    }
}
