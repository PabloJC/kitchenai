package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * Regenerates the requester's kitchen join code. Checked here, not only in the Firestore rules
 * (#192): only the owner may do this, the same defence-in-depth every write use case applies.
 */
class RegenerateKitchenJoinCodeUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(requesterId: UserId): AppResult<Kitchen> {
        val kitchen = kitchens.getMyKitchen(requesterId).getOrElse { return AppResult.Failure(it) }
        if (kitchen.ownerId != requesterId) return AppResult.Failure(AppError.Unauthorized())
        return kitchens.regenerateJoinCode(kitchen.id, requesterId)
    }
}
