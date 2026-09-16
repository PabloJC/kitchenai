package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * Returns the kitchen this user belongs to, creating a solo one only when
 * [KitchenRepositoryContract.getMyKitchen] fails with [AppError.NotFound] — any other failure
 * is propagated, never mistaken for "no kitchen yet". Idempotent by design.
 */
class EnsureKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(
        userId: UserId,
        displayName: String?,
    ): AppResult<Kitchen> =
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
