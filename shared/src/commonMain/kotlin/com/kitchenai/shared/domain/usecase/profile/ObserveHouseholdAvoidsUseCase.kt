package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.HouseholdAvoids
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.householdAvoids
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Every member's Avoid set in [userId]'s kitchen, pooled: what a recipe on screen is checked against. */
class ObserveHouseholdAvoidsUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    operator fun invoke(userId: UserId): Flow<HouseholdAvoids> =
        kitchens.observeMyKitchen(userId).map { it.householdAvoids() }.distinctUntilChanged()

    /** The listener's failures, collected alongside the stream above. */
    fun errors(userId: UserId): Flow<AppError> = kitchens.kitchenErrors(userId)
}
