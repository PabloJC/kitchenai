package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * Leaves the caller's current kitchen, if any, before joining another: a user belongs to
 * exactly one at a time, and joining never merges data (#186 accepted the same trade-off for
 * the Google sign-in switch). Rejected under the same owner rule as `LeaveKitchenUseCase`.
 * [AppError.NotFound] from [KitchenRepositoryContract.getMyKitchen] means "nothing to leave",
 * not a failure; any other error stops the join.
 */
class JoinKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen> {
        when (val current = kitchens.getMyKitchen(userId)) {
            is AppResult.Success -> {
                val kitchen = current.data
                if (kitchen.ownerId == userId && kitchen.memberIds.size > 1) {
                    return AppResult.Failure(
                        AppError.Validation("kitchen", "owner cannot leave a kitchen with other members"),
                    )
                }
                val left = kitchens.leaveKitchen(userId, kitchen.id)
                if (left is AppResult.Failure) return left
            }
            is AppResult.Failure -> if (current.error !is AppError.NotFound) return current
        }
        return kitchens.joinKitchen(userId, displayName, joinCode)
    }
}
