package com.kitchenai.ui.presentation.onboarding

import com.kitchenai.shared.domain.usecase.kitchen.JoinKitchenUseCase
import com.kitchenai.shared.domain.usecase.onboarding.CompleteOnboardingUseCase
import com.kitchenai.shared.domain.usecase.pantry.AddStaplesUseCase

/** What the first run changes: the device's flag and the pantry's quick start. It decides nothing. */
class OnboardingWritesDelegate(
    val complete: CompleteOnboardingUseCase,
    val addStaples: AddStaplesUseCase,
    val join: JoinKitchenUseCase,
)
