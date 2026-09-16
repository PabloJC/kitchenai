package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import kotlinx.coroutines.flow.firstOrNull

/**
 * Returns the kitchen this user belongs to, creating a solo one only when none references
 * them yet. `firstOrNull` reads "no kitchen document for this uid" as the stream ending empty,
 * the same shape `EnsureSessionUseCase` (#186) reads "no session" from. Idempotent by design.
 */
class EnsureKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(
        userId: UserId,
        displayName: String?,
    ): AppResult<Kitchen> {
        val existing = kitchens.observeMyKitchen(userId).firstOrNull()
        if (existing != null) return AppResult.Success(existing)
        return kitchens.createKitchen(userId, displayName)
    }
}
