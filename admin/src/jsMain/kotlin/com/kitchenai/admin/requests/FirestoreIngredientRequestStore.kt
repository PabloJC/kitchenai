package com.kitchenai.admin.requests

import com.kitchenai.admin.core.catchingFailures
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Admin-only, so the collection is named here rather than in `:shared`'s FirestorePaths. */
class FirestoreIngredientRequestStore(
    private val firestore: FirebaseFirestore,
) : IngredientRequestStore {
    private fun requests() = firestore.collection(COLLECTION)

    override fun observeRequests(): Flow<Result<List<IngredientRequest>>> =
        requests()
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
            requests()
                .document(id)
                .updateFields {
                    STATUS to status.wire
                    if (ingredientId != null) INGREDIENT_ID to ingredientId else INGREDIENT_ID to FieldValue.delete
                }
        }

    private companion object {
        const val COLLECTION = "ingredientRequests"
        const val STATUS = "status"
        const val INGREDIENT_ID = "ingredientId"
    }
}
