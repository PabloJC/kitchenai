package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Appearance
import com.kitchenai.shared.domain.port.AppearanceRepositoryContract

class SaveAppearanceUseCase(
    private val appearance: AppearanceRepositoryContract,
) {
    suspend operator fun invoke(choice: Appearance): AppResult<Unit> = appearance.saveAppearance(choice)
}
