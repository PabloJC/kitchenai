package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.leaveRefusalFor
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * Joins another kitchen, moving the caller out of their current one if they have any: a user
 * belongs to exactly one at a time, and joining never merges data (#186 accepted the same
 * trade-off for the Google sign-in switch).
 *
 * The kitchen being left is resolved here and handed to [KitchenRepositoryContract.joinKitchen],
 * which leaves and joins as one write: a code that resolves to nothing, or a kitchen that refuses
 * the caller, changes no membership. The owner rule is the one [LeaveKitchenUseCase] applies,
 * checked before anything is written; [AppError.NotFound] from the read means "nothing to leave".
 */
class JoinKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
    private val lock: KitchenMembershipLock,
) {
    suspend operator fun invoke(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen> =
        lock.withLock {
            val leaving: KitchenId? =
                when (val current = kitchens.getMyKitchen(userId)) {
                    is AppResult.Success -> {
                        current.data.leaveRefusalFor(userId)?.let { return@withLock AppResult.Failure(it) }
                        current.data.id
                    }
                    is AppResult.Failure -> if (current.error is AppError.NotFound) null else return@withLock current
                }
            kitchens.joinKitchen(userId, displayName, joinCode, leaving)
        }
}
