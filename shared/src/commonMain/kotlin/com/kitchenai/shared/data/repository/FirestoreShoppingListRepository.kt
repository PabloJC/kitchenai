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
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.ShoppingList
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
 *
 * Still keyed by the uid the path was built for, via [KitchenId.asUserId] — #192 repoints
 * [FirestorePaths] itself at `kitchens/{kitchenId}/...`, at which point this bridging disappears.
 */
class FirestoreShoppingListRepository(
    private val paths: FirestorePaths,
    private val dispatchers: DispatcherProvider,
) : ShoppingListRepositoryContract {
    // Writes outlive the caller on purpose; the supervisor keeps one failure from cancelling
    // the writes queued after it.
    private val writes = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val errors = KeyedErrorSinks<KitchenId>()

    override fun observeLists(kitchenId: KitchenId): Flow<List<ShoppingList>> =
        paths
            .shoppingLists(kitchenId.asUserId())
            .snapshots
            .map { snapshot -> snapshot.toLists() }
            .reportingErrorsTo(errors.of(kitchenId))

    override fun listErrors(kitchenId: KitchenId): Flow<AppError> = errors.of(kitchenId).asSharedFlow()

    override suspend fun getLists(kitchenId: KitchenId): AppResult<List<ShoppingList>> =
        firestoreCall(dispatchers) { paths.shoppingLists(kitchenId.asUserId()).get().toLists() }

    override suspend fun upsertList(
        kitchenId: KitchenId,
        list: ShoppingList,
    ): AppResult<Unit> =
        // `updatedAtMillis` travels in the document the domain built, so ordering stays stable
        // without the repository owning a clock.
        writes.optimistically(errors.of(kitchenId)) {
            paths.shoppingList(kitchenId.asUserId(), list.id).set(list.toDto(), merge = true) { encodeDefaults = true }
        }

    // A document that will not map is dropped, never propagated as a failure for the whole list.
    private fun QuerySnapshot.toLists(): List<ShoppingList> = documents.map { it.toShoppingList() }.decodedOrDropped()

    private fun DocumentSnapshot.toShoppingList(): AppResult<ShoppingList> =
        runCatching { data(ShoppingListDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )
}
