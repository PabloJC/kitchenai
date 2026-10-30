package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * The owner hands their kitchen to another member, which is what lets them leave it without
 * removing everyone first. Checked here as well as in the rules: only the owner may, and only to
 * someone who is a member now.
 */
class TransferKitchenOwnershipUseCase(
    private val kitchens: KitchenRepositoryContract,
    private val lock: KitchenMembershipLock,
) {
    suspend operator fun invoke(
        requesterId: UserId,
        newOwnerId: UserId,
    ): AppResult<Unit> =
        lock.withLock {
            val kitchen = kitchens.getMyKitchen(requesterId).getOrElse { return@withLock AppResult.Failure(it) }
            val rejection =
                when {
                    kitchen.ownerId != requesterId -> AppError.Unauthorized()
                    newOwnerId == requesterId -> AppError.Validation("newOwnerId", "is already the owner")
                    newOwnerId !in kitchen.memberIds -> AppError.NotFound("member")
                    else -> null
                }
            if (rejection != null) return@withLock AppResult.Failure(rejection)
            kitchens.transferOwnership(kitchen.id, requesterId, newOwnerId)
        }
}
