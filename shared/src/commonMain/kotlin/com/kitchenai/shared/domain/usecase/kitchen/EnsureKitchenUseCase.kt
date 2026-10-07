package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * Returns the kitchen this user belongs to, creating a solo one only when
 * [KitchenRepositoryContract.getMyKitchen] fails with [AppError.NotFound] — any other failure
 * is propagated, never mistaken for "no kitchen yet". Idempotent by design; the read-then-create
 * holds [KitchenMembershipLock] so a concurrent join or a second caller cannot slip in between.
 */
class EnsureKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
    private val lock: KitchenMembershipLock,
) {
    suspend operator fun invoke(
        userId: UserId,
        displayName: String?,
    ): AppResult<Kitchen> =
        lock.withLock {
            when (val existing = kitchens.getMyKitchen(userId)) {
                is AppResult.Success -> existing
                is AppResult.Failure ->
                    if (existing.error is AppError.NotFound) {
                        kitchens.createKitchen(userId, displayName)
                    } else {
                        existing
                    }
            }
        }
}
