package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import kotlinx.coroutines.flow.Flow

/** The kitchen-membership seam: `domain` states what it needs from storage, `data` provides it. */
interface KitchenRepositoryContract {
    fun observeMyKitchen(userId: UserId): Flow<Kitchen>

    /** Failures of the listener above, which stops emitting rather than throwing. */
    fun kitchenErrors(userId: UserId): Flow<AppError>

    /**
     * One-shot read for the write use cases: taking the first emission of [observeMyKitchen]
     * would hang forever once that listener has failed, and cannot tell "no kitchen yet" apart
     * from "the read failed" (decision #68, `docs/mvp-backlog.md`). No kitchen document for this
     * uid fails as [AppError.NotFound], not as an empty success.
     */
    suspend fun getMyKitchen(userId: UserId): AppResult<Kitchen>

    suspend fun createKitchen(
        ownerId: UserId,
        displayName: String?,
    ): AppResult<Kitchen>

    suspend fun joinKitchen(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen>

    suspend fun leaveKitchen(
        userId: UserId,
        kitchenId: KitchenId,
    ): AppResult<Unit>

    suspend fun removeMember(
        kitchenId: KitchenId,
        requesterId: UserId,
        memberId: UserId,
    ): AppResult<Unit>

    suspend fun regenerateJoinCode(
        kitchenId: KitchenId,
        requesterId: UserId,
    ): AppResult<Kitchen>

    /** Refreshes the caller's own entry in [Kitchen.memberDisplayNames]; never another member's. */
    suspend fun updateMyDisplayName(
        userId: UserId,
        kitchenId: KitchenId,
        displayName: String,
    ): AppResult<Unit>
}
