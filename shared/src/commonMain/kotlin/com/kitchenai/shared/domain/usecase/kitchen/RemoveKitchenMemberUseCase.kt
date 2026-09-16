package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * Removes a member from the requester's kitchen. Checked here, not only in the Firestore rules
 * (#192): only the owner may do this, the same defence-in-depth every write use case applies.
 * An owner removing themself, or a `memberId` that never belonged to this kitchen, are both
 * rejected — the former has its own, ownership-aware path in [LeaveKitchenUseCase].
 */
class RemoveKitchenMemberUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(
        requesterId: UserId,
        memberId: UserId,
    ): AppResult<Unit> {
        val kitchen = kitchens.getMyKitchen(requesterId).getOrElse { return AppResult.Failure(it) }
        val rejection =
            when {
                kitchen.ownerId != requesterId -> AppError.Unauthorized()
                memberId == requesterId ->
                    AppError.Validation(
                        "memberId",
                        "the owner cannot remove themself, only leave",
                    )
                memberId !in kitchen.memberIds -> AppError.NotFound("member")
                else -> null
            }
        if (rejection != null) return AppResult.Failure(rejection)
        return kitchens.removeMember(kitchen.id, requesterId, memberId)
    }
}
