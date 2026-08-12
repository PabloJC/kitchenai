package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.remote.firebase.firestoreCall
import com.kitchenai.shared.data.remote.firebase.reportingErrorsTo
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.RecipePort
import com.kitchenai.shared.domain.port.TimeProvider
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
 * [RecipePort] over the read-only `recipes` catalogue and `users/{uid}/savedRecipes`.
 *
 * A saved recipe is a snapshot rather than a reference: a generated one has nothing to point at,
 * and a catalogue one that changes under the user is a support ticket. The cost is duplication,
 * measured in kilobytes.
 */
class FirestoreRecipeRepository(
    private val paths: FirestorePaths,
    private val auth: FirebaseAuth,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : RecipePort {
    // Writes outlive the caller on purpose; the supervisor keeps one failure from cancelling
    // the writes queued after it.
    private val writes = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val errors = KeyedErrorSinks<UserId>()

    override fun observeSavedRecipes(userId: UserId): Flow<List<Recipe>> =
        paths
            .savedRecipes(userId)
            .orderBy(SAVED_AT, Direction.DESCENDING)
            .snapshots
            .map { snapshot -> snapshot.toRecipes() }
            .reportingErrorsTo(errors.of(userId))

    override fun savedRecipeErrors(userId: UserId): Flow<AppError> = errors.of(userId).asSharedFlow()

    override suspend fun getRecipe(recipeId: RecipeId): AppResult<Recipe> =
        firstFound(
            RECIPE_RESOURCE,
            listOf({ savedCopy(recipeId) }, { cataloguedCopy(recipeId) }),
        )

    override suspend fun saveRecipe(
        userId: UserId,
        recipe: Recipe,
    ): AppResult<Unit> {
        val document = recipe.toDto(time.now())
        return when (val guarded = document.withinDocumentLimit()) {
            is AppResult.Failure -> guarded
            is AppResult.Success -> writes.optimistically(errors.of(userId)) { write(userId, recipe.id, document) }
        }
    }

    override suspend fun removeSavedRecipe(
        userId: UserId,
        recipeId: RecipeId,
    ): AppResult<Unit> = writes.optimistically(errors.of(userId)) { paths.savedRecipe(userId, recipeId).delete() }

    // No merge: it would leave the steps of a previous version of this id next to the new ones.
    private suspend fun write(
        userId: UserId,
        recipeId: RecipeId,
        document: RecipeDto,
    ) = paths.savedRecipe(userId, recipeId).set(document, merge = false) { encodeDefaults = true }

    /** Absent for a signed-out reader, who still gets the catalogue rather than a failure. */
    private suspend fun savedCopy(recipeId: RecipeId): AppResult<Recipe?> {
        val userId = currentUserId() ?: return AppResult.Success(null)
        return firestoreCall(dispatchers) { paths.savedRecipe(userId, recipeId).get() }.decoded()
    }

    private suspend fun cataloguedCopy(recipeId: RecipeId): AppResult<Recipe?> =
        firestoreCall(dispatchers) { paths.recipe(recipeId).get() }.decoded()

    private fun currentUserId(): UserId? =
        auth.currentUser?.uid?.let { uid -> (UserId.of(uid) as? AppResult.Success)?.data }

    private fun AppResult<DocumentSnapshot>.decoded(): AppResult<Recipe?> =
        when (this) {
            is AppResult.Failure -> this
            is AppResult.Success -> if (data.exists) data.toRecipe() else AppResult.Success(null)
        }

    // A document that will not map is dropped, never propagated as a failure for the whole library.
    private fun QuerySnapshot.toRecipes(): List<Recipe> = documents.map { it.toRecipe() }.decodedOrDropped()

    private fun DocumentSnapshot.toRecipe(): AppResult<Recipe> =
        runCatching { data(RecipeDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )

    private companion object {
        // The collection, never the identifier: an error carries no user content.
        const val RECIPE_RESOURCE = "recipe"
        const val SAVED_AT = "savedAtMillis"
    }
}
