package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import kotlinx.coroutines.flow.Flow

/** The pantry seam: `domain` states what it needs from storage, `data` provides it. */
interface PantryRepositoryContract {
    fun observePantry(kitchenId: KitchenId): Flow<List<PantryItem>>

    /** Failures of the listener above, which stops emitting rather than throwing. */
    fun pantryErrors(kitchenId: KitchenId): Flow<AppError>

    /**
     * One-shot read for the read-modify-write use cases: taking the first emission of the
     * listener would hang forever once that listener has failed.
     */
    suspend fun getPantry(kitchenId: KitchenId): AppResult<List<PantryItem>>

    suspend fun upsert(
        kitchenId: KitchenId,
        item: PantryItem,
    ): AppResult<Unit>

    suspend fun remove(
        kitchenId: KitchenId,
        id: PantryItemId,
    ): AppResult<Unit>

    /** Batched so that "I cooked this recipe" is one write instead of N round trips. */
    suspend fun upsertAll(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ): AppResult<Unit>

    /**
     * Like [upsertAll], but waits for the server to acknowledge the write instead of returning
     * immediately — for the rare caller whose next step must not run until this one has
     * genuinely landed, not merely been accepted into the local cache.
     */
    suspend fun upsertAllConfirmed(
        kitchenId: KitchenId,
        items: List<PantryItem>,
    ): AppResult<Unit>
}
