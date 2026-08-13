package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.ShoppingItemDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.remote.firebase.firestoreCall
import com.kitchenai.shared.data.remote.firebase.reportingErrorsTo
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ShoppingItemPort
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * [ShoppingItemPort] over `users/{uid}/shoppingLists/{listId}/items`. Everything is keyed by the
 * list, streams and error sinks alike: a screen watching one list downloads and hears about that
 * list only.
 */
class FirestoreShoppingItemRepository(
    private val paths: FirestorePaths,
    private val firestore: FirebaseFirestore,
    private val dispatchers: DispatcherProvider,
) : ShoppingItemPort {
    // Writes outlive the caller on purpose; the supervisor keeps one failure from cancelling
    // the writes queued after it.
    private val writes = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val errors = KeyedErrorSinks<Pair<UserId, ShoppingListId>>()

    override fun observeItems(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<List<ShoppingItem>> =
        paths
            .shoppingListItems(userId, listId)
            .snapshots
            .map { snapshot -> snapshot.toItems() }
            .reportingErrorsTo(errors.of(userId to listId))

    override fun itemErrors(
        userId: UserId,
        listId: ShoppingListId,
    ): Flow<AppError> = errors.of(userId to listId).asSharedFlow()

    override suspend fun getItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<List<ShoppingItem>> =
        firestoreCall(dispatchers) { paths.shoppingListItems(userId, listId).get().toItems() }

    override suspend fun upsertItems(
        userId: UserId,
        listId: ShoppingListId,
        items: List<ShoppingItem>,
    ): AppResult<Unit> =
        writes.optimistically(errors.of(userId to listId)) {
            items.chunkedForBatch().forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { item ->
                    val document = paths.shoppingListItem(userId, listId, item.id)
                    batch.set(document, item.toDto(), merge = true) { encodeDefaults = true }
                }
                batch.commit()
            }
        }

    override suspend fun removeItem(
        userId: UserId,
        listId: ShoppingListId,
        itemId: ShoppingItemId,
    ): AppResult<Unit> =
        writes.optimistically(errors.of(userId to listId)) {
            paths.shoppingListItem(userId, listId, itemId).delete()
        }

    /**
     * Reads which lines are ticked, then deletes them without waiting for the server. The read
     * is served from the cache while offline, which is where a ticked line already is.
     */
    override suspend fun removeCheckedItems(
        userId: UserId,
        listId: ShoppingListId,
    ): AppResult<Unit> {
        val ticked = firestoreCall(dispatchers) { checkedDocuments(userId, listId) }
        return when (ticked) {
            is AppResult.Failure -> ticked
            is AppResult.Success -> writes.optimistically(errors.of(userId to listId)) { deleteAll(ticked.data) }
        }
    }

    private suspend fun checkedDocuments(
        userId: UserId,
        listId: ShoppingListId,
    ): List<DocumentReference> =
        paths
            .shoppingListItems(userId, listId)
            .where { CHECKED equalTo true }
            .get()
            .documents
            .map { it.reference }

    private suspend fun deleteAll(documents: List<DocumentReference>) {
        documents.chunkedForBatch().forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { document -> batch.delete(document) }
            batch.commit()
        }
    }

    // A document that will not map is dropped, never propagated as a failure for the whole list.
    private fun QuerySnapshot.toItems(): List<ShoppingItem> = documents.map { it.toItem() }.decodedOrDropped()

    private fun DocumentSnapshot.toItem(): AppResult<ShoppingItem> =
        runCatching { data(ShoppingItemDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )

    private companion object {
        const val CHECKED = "checked"
    }
}
