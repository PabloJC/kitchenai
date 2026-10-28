package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.HouseholdAvoids
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.KitchenRepositoryContract

/**
 * Mirrors [profile]'s Avoid set onto [kitchen], so ideas any member asks for respect it. Run on
 * every change to either: a join, a profile edit, or a kitchen still missing the entry. A no-op
 * when the kitchen already holds it, which is also what stops the write's own echo looping.
 */
class ShareMyAvoidsUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    suspend operator fun invoke(
        kitchen: Kitchen,
        profile: UserProfile,
    ): AppResult<Unit> {
        val mine = HouseholdAvoids.of(profile)
        val shared = kitchen.memberAvoids[profile.userId.value] ?: HouseholdAvoids()
        if (profile.userId !in kitchen.memberIds || shared == mine) return AppResult.Success(Unit)
        return kitchens.updateMyAvoids(profile.userId, kitchen.id, mine)
    }
}
