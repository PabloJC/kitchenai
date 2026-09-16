package com.kitchenai.shared.domain.usecase.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.KitchenRepositoryContract
import kotlinx.coroutines.flow.Flow

/** The kitchen stream for the current user, mirroring `ObservePantryUseCase`'s error split. */
class ObserveKitchenUseCase(
    private val kitchens: KitchenRepositoryContract,
) {
    operator fun invoke(userId: UserId): Flow<Kitchen> = kitchens.observeMyKitchen(userId)

    /** The listener's failures, collected alongside the stream above. */
    fun errors(userId: UserId): Flow<AppError> = kitchens.kitchenErrors(userId)
}
