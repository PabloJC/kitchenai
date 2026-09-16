package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import kotlinx.coroutines.flow.firstOrNull

/**
 * Leaves the caller's current kitchen. The owner of one with other members is rejected here:
 * they would strand it without anyone able to remove people or regenerate its join code, and
 * ownership never transfers in the MVP.
 */
class LeaveKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(userId: UserId): AppResult<Unit> {
        val current =
            kitchens.observeMyKitchen(userId).firstOrNull()
                ?: return AppResult.Failure(AppError.NotFound("kitchen"))
        if (current.ownerId == userId && current.memberIds.size > 1) {
            return AppResult.Failure(AppError.Validation("kitchen", "owner cannot leave a kitchen with other members"))
        }
        return kitchens.leaveKitchen(userId, current.id)
    }
}
