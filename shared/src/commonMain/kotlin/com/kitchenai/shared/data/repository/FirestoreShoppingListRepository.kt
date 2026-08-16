package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.ShoppingListDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.remote.firebase.firestoreCall
import com.kitchenai.shared.data.remote.firebase.reportingErrorsTo
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingListRepositoryContract
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * [ShoppingListRepositoryContract] over `users/{uid}/shoppingLists`: a snapshot listener to read, an
 * optimistic merge write to change. The items of a list are a separate collection behind
 * [com.kitchenai.shared.data.repository.FirestoreShoppingItemRepository].
 */
class FirestoreShoppingListRepository(
    private val paths: FirestorePaths,
    private val dispatchers: DispatcherProvider,
) : ShoppingListRepositoryContract {
    // Writes outlive the caller on purpose; the supervisor keeps one failure from cancelling
    // the writes queued after it.
    private val writes = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val errors = KeyedErrorSinks<UserId>()

    override fun observeLists(userId: UserId): Flow<List<ShoppingList>> =
        paths
            .shoppingLists(userId)
            .snapshots
            .map { snapshot -> snapshot.toLists() }
            .reportingErrorsTo(errors.of(userId))

    override fun listErrors(userId: UserId): Flow<AppError> = errors.of(userId).asSharedFlow()

    override suspend fun getLists(userId: UserId): AppResult<List<ShoppingList>> =
        firestoreCall(dispatchers) { paths.shoppingLists(userId).get().toLists() }

    override suspend fun upsertList(
        userId: UserId,
        list: ShoppingList,
    ): AppResult<Unit> =
        // `updatedAtMillis` travels in the document the domain built, so ordering stays stable
        // without the repository owning a clock.
        writes.optimistically(errors.of(userId)) {
            paths.shoppingList(userId, list.id).set(list.toDto(), merge = true) { encodeDefaults = true }
        }

    // A document that will not map is dropped, never propagated as a failure for the whole list.
    private fun QuerySnapshot.toLists(): List<ShoppingList> = documents.map { it.toShoppingList() }.decodedOrDropped()

    private fun DocumentSnapshot.toShoppingList(): AppResult<ShoppingList> =
        runCatching { data(ShoppingListDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )
}
