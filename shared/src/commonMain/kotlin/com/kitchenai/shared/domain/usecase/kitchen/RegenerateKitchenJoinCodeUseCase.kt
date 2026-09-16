package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import kotlinx.coroutines.flow.firstOrNull

/**
 * Regenerates the requester's kitchen join code. Checked here, not only in the Firestore rules
 * (#192): only the owner may do this, the same defence-in-depth every write use case applies.
 */
class RegenerateKitchenJoinCodeUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(requesterId: UserId): AppResult<Kitchen> {
        val kitchen =
            kitchens.observeMyKitchen(requesterId).firstOrNull()
                ?: return AppResult.Failure(AppError.NotFound("kitchen"))
        if (kitchen.ownerId != requesterId) return AppResult.Failure(AppError.Unauthorized())
        return kitchens.regenerateJoinCode(kitchen.id, requesterId)
    }
}
