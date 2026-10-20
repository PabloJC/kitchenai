package com.kitchenai.ui.presentation.onboarding

import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.onboarding.ObserveOnboardingCompletedUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase

/** What the first run reads. Like the other screens' delegates, it holds no logic and decides nothing. */
class OnboardingReadsDelegate(
    val completed: ObserveOnboardingCompletedUseCase,
    val kitchen: ObserveKitchenUseCase,
    val pantry: ObservePantryUseCase,
    val ingredients: ObserveIngredientsUseCase,
)
