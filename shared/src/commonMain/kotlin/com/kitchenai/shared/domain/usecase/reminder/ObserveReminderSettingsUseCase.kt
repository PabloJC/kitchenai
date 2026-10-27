package com.kitchenai.shared.domain.usecase.reminder

import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.port.ReminderSettingsRepositoryContract
import kotlinx.coroutines.flow.Flow

class ObserveReminderSettingsUseCase(
    private val settings: ReminderSettingsRepositoryContract,
) {
    operator fun invoke(): Flow<ReminderSettings> = settings.observeSettings()
}
