package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.leaveRefusalFor
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * Leaves the caller's current kitchen. The owner of one with other members is rejected here:
 * they would strand it without anyone able to remove people or regenerate its join code, and
 * ownership never transfers in the MVP. No replacement kitchen is made here: the session
 * provisions one once the kitchen listener reports none (`docs/session.md`).
 */
class LeaveKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
    private val lock: KitchenMembershipLock,
) {
    suspend operator fun invoke(userId: UserId): AppResult<Unit> =
        lock.withLock {
            val current = kitchens.getMyKitchen(userId).getOrElse { return@withLock AppResult.Failure(it) }
            current.leaveRefusalFor(userId)?.let { return@withLock AppResult.Failure(it) }
            kitchens.leaveKitchen(userId, current.id)
        }
}
