package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/** The pantry seam: `domain` states what it needs from storage, `data` provides it. */
interface PantryRepositoryContract {
    fun observePantry(userId: UserId): Flow<List<PantryItem>>

    /** Failures of the listener above, which stops emitting rather than throwing. */
    fun pantryErrors(userId: UserId): Flow<AppError>

    /**
     * One-shot read for the read-modify-write use cases: taking the first emission of the
     * listener would hang forever once that listener has failed.
     */
    suspend fun getPantry(userId: UserId): AppResult<List<PantryItem>>

    suspend fun upsert(
        userId: UserId,
        item: PantryItem,
    ): AppResult<Unit>

    suspend fun remove(
        userId: UserId,
        id: PantryItemId,
    ): AppResult<Unit>

    /** Batched so that "I cooked this recipe" is one write instead of N round trips. */
    suspend fun upsertAll(
        userId: UserId,
        items: List<PantryItem>,
    ): AppResult<Unit>
}
