package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.port.ReminderSettingsRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The device's reminder setting in memory; [failWith] refuses the next writes. */
class FakeReminderSettingsPort : ReminderSettingsRepositoryContract {
    val current = MutableStateFlow(ReminderSettings())
    var failWith: AppError? = null

    override fun observeSettings(): Flow<ReminderSettings> = current

    override suspend fun saveSettings(settings: ReminderSettings): AppResult<Unit> {
        failWith?.let { return AppResult.Failure(it) }
        current.value = settings
        return AppResult.Success(Unit)
    }
}
