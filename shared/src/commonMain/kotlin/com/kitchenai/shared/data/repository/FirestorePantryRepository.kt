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
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.PantryPort
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * [PantryPort] over `users/{uid}/pantry`: a snapshot listener to read, optimistic writes to
 * change. GitLive's `set` and `delete` only resolve once the server acknowledges them, so
 * awaiting one would leave the user watching a spinner for a write the cache already applied.
 */
class FirestorePantryRepository(
    private val paths: FirestorePaths,
    private val firestore: FirebaseFirestore,
    private val dispatchers: DispatcherProvider,
) : PantryPort {
    // Writes outlive the caller on purpose; the supervisor keeps one failure from cancelling
    // the writes queued after it.
    private val writes = CoroutineScope(SupervisorJob() + dispatchers.io)

    // One sink per user: a single stream per port would report one listener's failure to every
    // other open observer.
    private val sinks = MutableStateFlow<Map<UserId, MutableSharedFlow<AppError>>>(emptyMap())

    override fun observePantry(userId: UserId): Flow<List<PantryItem>> =
        paths
            .pantry(userId)
            .snapshots
            .map { snapshot -> snapshot.toPantryItems() }
            .reportingErrorsTo(sink(userId))

    override fun pantryErrors(userId: UserId): Flow<AppError> = sink(userId).asSharedFlow()

    override suspend fun getPantry(userId: UserId): AppResult<List<PantryItem>> =
        firestoreCall(dispatchers) { paths.pantry(userId).get().toPantryItems() }

    override suspend fun upsert(
        userId: UserId,
        item: PantryItem,
    ): AppResult<Unit> =
        optimistically(userId) {
            paths.pantryItem(userId, item.id).set(item.toDto(), merge = true) { encodeDefaults = true }
        }

    override suspend fun remove(
        userId: UserId,
        id: PantryItemId,
    ): AppResult<Unit> = optimistically(userId) { paths.pantryItem(userId, id).delete() }

    override suspend fun upsertAll(
        userId: UserId,
        items: List<PantryItem>,
    ): AppResult<Unit> =
        optimistically(userId) {
            val batch = firestore.batch()
            items.forEach { item ->
                batch.set(paths.pantryItem(userId, item.id), item.toDto(), merge = true) { encodeDefaults = true }
            }
            batch.commit()
        }

    /**
     * Issues [write] against the local cache and returns without waiting for the server. A
     * failure that does arrive later is published on the user's error stream rather than
     * swallowed; offline is not one of them, since Firestore replays the write when it can.
     */
    private fun optimistically(
        userId: UserId,
        write: suspend () -> Unit,
    ): AppResult<Unit> {
        writes.launch {
            runCatching { write() }.onFailure { failure -> sink(userId).emit(failure.toAppError()) }
        }
        return AppResult.Success(Unit)
    }

    // The dropped count is deliberately not consumed: there is no metric sink in the MVP, and a
    // log line would carry the user's own food.
    private fun QuerySnapshot.toPantryItems(): List<PantryItem> = documents.map { it.toPantryItem() }.mapped().items

    private fun DocumentSnapshot.toPantryItem(): AppResult<PantryItem> =
        runCatching { data(PantryItemDto.serializer()) }.fold(
            onSuccess = { dto -> dto.toDomain(id) },
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )

    private fun sink(userId: UserId): MutableSharedFlow<AppError> =
        sinks
            .updateAndGet { open -> if (userId in open) open else open + (userId to newSink()) }
            .getValue(userId)

    // Buffered so that publishing a failure never suspends the listener that is dying.
    private fun newSink(): MutableSharedFlow<AppError> = MutableSharedFlow(extraBufferCapacity = 1)
}
