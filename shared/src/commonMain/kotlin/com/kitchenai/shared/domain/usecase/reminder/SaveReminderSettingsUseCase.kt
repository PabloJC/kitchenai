package com.kitchenai.shared.domain.usecase.reminder

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.port.ReminderSettingsRepositoryContract

/** A time that is not on a clock is refused here rather than stored and silently never fired. */
class SaveReminderSettingsUseCase(
    private val settings: ReminderSettingsRepositoryContract,
) {
    suspend operator fun invoke(next: ReminderSettings): AppResult<Unit> =
        if (next.hour !in HOURS || next.minute !in MINUTES) {
            AppResult.Failure(AppError.Validation("time", "not a time of day"))
        } else {
            settings.saveSettings(next)
        }

    private companion object {
        val HOURS = 0..23
        val MINUTES = 0..59
    }
}
