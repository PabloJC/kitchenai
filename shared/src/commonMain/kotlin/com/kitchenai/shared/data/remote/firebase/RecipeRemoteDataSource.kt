package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.data.repository.KeyedErrorSinks
import com.kitchenai.shared.data.repository.decodedOrDropped
import com.kitchenai.shared.data.repository.optimistically
import com.kitchenai.shared.data.repository.withinDocumentLimit
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * The remote half of the recipe data sources: the read-only `recipes` catalogue and
 * `kitchens/{kitchenId}/savedRecipes`, over Firestore. It knows nothing about
 * [com.kitchenai.shared.domain.model.Recipe] — that mapping, and deciding in which order a saved
 * copy and the catalogue are read, is
 * [com.kitchenai.shared.data.repository.RecipeRepository]'s job. What it owns is everything
 * Firestore-specific instead: paths, snapshot decoding, the signed-in user's id, and queuing
 * writes optimistically.
 *
 * A saved recipe is a snapshot rather than a reference: a generated one has nothing to point at,
 * and a catalogue one that changes under the user is a support ticket. The cost is duplication,
 * measured in kilobytes.
 */
class RecipeRemoteDataSource(
    private val paths: FirestorePaths,
    private val auth: FirebaseAuth,
    private val dispatchers: DispatcherProvider,
) {
    // Writes outlive the caller on purpose; the supervisor keeps one failure from cancelling
    // the writes queued after it.
    private val writes = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val errors = KeyedErrorSinks<KitchenId>()

    /** The signed-in user, not a kitchen: the repository this feeds resolves that itself. */
    fun currentUserId(): UserId? = auth.currentUser?.uid?.let { uid -> (UserId.of(uid) as? AppResult.Success)?.data }

    fun observeSavedRecipes(kitchenId: KitchenId): Flow<List<RecipeDocument>> =
        paths
            .savedRecipes(kitchenId)
            .orderBy(SAVED_AT, Direction.DESCENDING)
            .snapshots
            .map { snapshot -> snapshot.toRecipeDocuments() }
            .reportingErrorsTo(errors.of(kitchenId))

    fun savedRecipeErrors(kitchenId: KitchenId): Flow<AppError> = errors.of(kitchenId).asSharedFlow()

    suspend fun getSavedRecipe(
        kitchenId: KitchenId,
        recipeId: RecipeId,
    ): AppResult<RecipeDocument?> =
        firestoreCall(
            dispatchers,
        ) { paths.savedRecipe(kitchenId, recipeId).get() }.decoded()

    suspend fun getCataloguedRecipe(recipeId: RecipeId): AppResult<RecipeDocument?> =
        firestoreCall(dispatchers) { paths.recipe(recipeId).get() }.decoded()

    suspend fun save(
        kitchenId: KitchenId,
        recipeId: RecipeId,
        dto: RecipeDto,
    ): AppResult<Unit> =
        when (val guarded = dto.withinDocumentLimit()) {
            is AppResult.Failure -> guarded
            is AppResult.Success -> writes.optimistically(errors.of(kitchenId)) { write(kitchenId, recipeId, dto) }
        }

    suspend fun remove(
        kitchenId: KitchenId,
        recipeId: RecipeId,
    ): AppResult<Unit> = writes.optimistically(errors.of(kitchenId)) { paths.savedRecipe(kitchenId, recipeId).delete() }

    // No merge: it would leave the steps of a previous version of this id next to the new ones.
    private suspend fun write(
        kitchenId: KitchenId,
        recipeId: RecipeId,
        dto: RecipeDto,
    ) = paths.savedRecipe(kitchenId, recipeId).set(dto, merge = false) { encodeDefaults = true }

    private fun AppResult<DocumentSnapshot>.decoded(): AppResult<RecipeDocument?> =
        when (this) {
            is AppResult.Failure -> this
            is AppResult.Success -> if (data.exists) data.toRecipeDocument() else AppResult.Success(null)
        }

    // A document that will not decode is dropped, never propagated as a failure for the whole library.
    private fun QuerySnapshot.toRecipeDocuments(): List<RecipeDocument> =
        documents.map { it.toRecipeDocument() }.decodedOrDropped()

    private fun DocumentSnapshot.toRecipeDocument(): AppResult<RecipeDocument> =
        runCatching { data(RecipeDto.serializer()) }.fold(
            onSuccess = { dto -> AppResult.Success(RecipeDocument(id, dto)) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )

    private companion object {
        const val SAVED_AT = "savedAtMillis"
    }
}
