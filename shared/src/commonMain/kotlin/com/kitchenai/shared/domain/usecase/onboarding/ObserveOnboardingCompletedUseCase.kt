package com.kitchenai.shared.domain.usecase.onboarding

import com.kitchenai.shared.domain.port.OnboardingRepositoryContract
import kotlinx.coroutines.flow.Flow

class ObserveOnboardingCompletedUseCase(
    private val onboarding: OnboardingRepositoryContract,
) {
    operator fun invoke(): Flow<Boolean> = onboarding.observeCompleted()
}
