package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.domain.model.Appearance
import com.kitchenai.shared.domain.port.AppearanceRepositoryContract
import kotlinx.coroutines.flow.Flow

class ObserveAppearanceUseCase(
    private val appearance: AppearanceRepositoryContract,
) {
    operator fun invoke(): Flow<Appearance> = appearance.observeAppearance()
}
