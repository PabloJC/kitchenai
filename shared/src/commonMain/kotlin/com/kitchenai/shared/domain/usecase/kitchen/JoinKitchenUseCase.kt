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
 * the Google sign-in switch). Delegates to [LeaveKitchenUseCase] rather than re-checking the
 * owner rule itself, so that rule lives in exactly one place. [AppError.NotFound] out of it
 * means "nothing to leave", not a failure; any other error stops the join.
 *
 * Leaving and joining are two separate calls, not one transaction: a [leave] that succeeds
 * followed by a [KitchenRepositoryContract.joinKitchen] that fails leaves the caller in neither
 * kitchen — exactly the "no kitchen" state this feature otherwise guarantees never happens.
 * Closing that gap belongs in the real repository implementation (#192), as one Firestore
 * transaction, not here.
 */
class JoinKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
    private val leave: LeaveKitchenUseCase,
) {
    suspend operator fun invoke(
        userId: UserId,
        displayName: String?,
        joinCode: KitchenJoinCode,
    ): AppResult<Kitchen> {
        val left = leave(userId)
        if (left is AppResult.Failure && left.error !is AppError.NotFound) return left
        return kitchens.joinKitchen(userId, displayName, joinCode)
    }
}
