package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.reminder.ObserveReminderSettingsUseCase
import com.kitchenai.shared.domain.usecase.reminder.PlanExpiryDigestsUseCase

/** What the reminder reads, and the pure planner it hands each snapshot to. It decides nothing itself. */
class ExpiryReminderReadsDelegate(
    val kitchen: ObserveKitchenUseCase,
    val pantry: ObservePantryUseCase,
    val settings: ObserveReminderSettingsUseCase,
    val plan: PlanExpiryDigestsUseCase,
)
