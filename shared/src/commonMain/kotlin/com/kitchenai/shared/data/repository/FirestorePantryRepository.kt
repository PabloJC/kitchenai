package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.mapper.toDomain
import com.kitchenai.shared.data.mapper.toDto
import com.kitchenai.shared.data.remote.dto.PantryItemDto
import com.kitchenai.shared.data.remote.firebase.FirestorePaths
import com.kitchenai.shared.data.remote.firebase.firestoreCall
import com.kitchenai.shared.data.remote.firebase.reportingErrorsTo
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.port.PantryRepositoryContract
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * [PantryRepositoryContract] over `kitchens/{kitchenId}/pantry`: a snapshot listener to read,
 * optimistic writes to change. GitLive's `set` and `delete` only resolve once the server
 * acknowledges them, so awaiting one would leave the user watching a spinner for a write the
 * cache already applied.
 */
class FirestorePantryRepository(
    private val paths: FirestorePaths,
    private val firestore: FirebaseFirestore,
    private val dispatchers: DispatcherProvider,
) : PantryRepositoryContract {
    // Writes outlive the caller on purpose; the supervisor keeps one failure from cancelling
    // the writes queued after it.
    private val writes = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val errors = KeyedErrorSinks<KitchenId>()

    override fun observePantry(kitchenId: KitchenId): Flow<List<PantryItem>> =
        paths
            .pantry(kitchenId)
            .snapshots
            .map { snapshot -> snapshot.toPantryItems() }
            .reportingErrorsTo(errors.of(kitchenId))

    override fun pantryErrors(kitchenId: KitchenId): Flow<AppError> = errors.of(kitchenId).asSharedFlow()

    override suspend fun getPantry(kitchenId: KitchenId): AppResult<List<PantryItem>> =
        firestoreCall(dispatchers) { paths.pantry(kitchenId).get().toPantryItems() }

    override suspend fun upsert(
        kitchenId: KitchenId,
        item: PantryItem,
    ): AppResult<Unit> =
        writes.optimistically(errors.of(kitchenId)) {
            paths.pantryItem(kitchenId, item.id).set(item.toDto(), merge = true) { encodeDefaults = true }
        }

    override suspend fun remove(
        kitchenId: KitchenId,
        id: PantryItemId,
    ): AppResult<Unit> =
        writes.optimistically(errors.of(kitchenId)) { paths.pantryItem(kitchenId, id).delete() }

    override suspend fun upsertAll(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ): AppResult<Unit> = writes.optimistically(errors.of(kitchenId)) { commitAll(kitchenId, items) }

    override suspend fun upsertAllConfirmed(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ): AppResult<Unit> = firestoreCall(dispatchers) { commitAll(kitchenId, items) }

    private suspend fun commitAll(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ) {
        val batch = firestore.batch()
        items.forEach { item ->
            batch.set(paths.pantryItem(kitchenId, item.id), item.toDto(), merge = true) { encodeDefaults = true }
        }
        batch.commit()
    }

    private fun QuerySnapshot.toPantryItems(): List<PantryItem> = documents.map { it.toPantryItem() }.decodedOrDropped()

    private fun DocumentSnapshot.toPantryItem(): AppResult<PantryItem> =
        runCatching { data(PantryItemDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )
}
