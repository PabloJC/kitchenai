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
}
