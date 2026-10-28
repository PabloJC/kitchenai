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
        val mine = HouseholdAvoids.of(profile).capped()
        val shared = kitchen.memberAvoids[profile.userId.value] ?: HouseholdAvoids()
        if (profile.userId !in kitchen.memberIds || shared == mine) return AppResult.Success(Unit)
        return kitchens.updateMyAvoids(profile.userId, kitchen.id, mine)
    }

    // The rules refuse a longer entry outright, which would share none of them; the callable reads no more either.
    private fun HouseholdAvoids.capped(): HouseholdAvoids =
        HouseholdAvoids(terms.take(MAX_SHARED).toSet(), ingredients.take(MAX_SHARED).toSet())

    companion object {
        /** `isValidOwnAvoids` in `firebase/firestore.rules`: at most this many of each. */
        const val MAX_SHARED = 100
    }
}
