package com.kitchenai.admin.requests

import com.kitchenai.admin.core.catchingFailures
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import dev.gitlive.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class FirestoreIngredientRequestStore(
    private val paths: FirestorePaths,
) : IngredientRequestStore {
    override fun observeRequests(): Flow<Result<List<IngredientRequest>>> =
        paths
            .ingredientRequests()
            .snapshots
            .map { snapshot ->
                Result.success(
                    snapshot.documents.mapNotNull { document ->
                        document.data(IngredientRequestDto.serializer()).toRequest(document.id)
                    },
                )
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(Result.failure(failure))
            }

    override suspend fun setStatus(
        id: String,
        status: RequestStatus,
        ingredientId: String?,
    ): Result<Unit> =
        catchingFailures {
            paths
                .ingredientRequests()
                .document(id)
                .updateFields {
                    STATUS to status.wire
                    if (ingredientId != null) INGREDIENT_ID to ingredientId else INGREDIENT_ID to FieldValue.delete
                }
        }

    private companion object {
        const val STATUS = "status"
        const val INGREDIENT_ID = "ingredientId"
    }
}
