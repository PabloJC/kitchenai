package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import kotlinx.coroutines.flow.firstOrNull

/**
 * Leaves the caller's current kitchen, if any, before joining another: a user belongs to
 * exactly one at a time, and joining never merges data (#186 accepted the same trade-off for
 * the Google sign-in switch). Rejected under the same owner rule as `LeaveKitchenUseCase`.
 */
class JoinKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen> {
        val current = kitchens.observeMyKitchen(userId).firstOrNull()
        if (current != null) {
            if (current.ownerId == userId && current.memberIds.size > 1) {
                return AppResult.Failure(
                    AppError.Validation("kitchen", "owner cannot leave a kitchen with other members"),
                )
            }
            val left = kitchens.leaveKitchen(userId, current.id)
            if (left is AppResult.Failure) return left
        }
        return kitchens.joinKitchen(userId, displayName, joinCode)
    }
}
