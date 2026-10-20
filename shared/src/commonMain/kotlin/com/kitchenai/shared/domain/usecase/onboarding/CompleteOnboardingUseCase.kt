package com.kitchenai.shared.domain.usecase.onboarding

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.port.OnboardingRepositoryContract

class CompleteOnboardingUseCase(
    private val onboarding: OnboardingRepositoryContract,
) {
    suspend operator fun invoke(): AppResult<Unit> = onboarding.markCompleted()
}
