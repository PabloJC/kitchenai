package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ReminderSettings
import kotlinx.coroutines.flow.Flow

/** This device's reminder settings; each device of a household reminds on its own terms. */
interface ReminderSettingsRepositoryContract {
    /** Emits at once, the defaults when nothing was ever saved. */
    fun observeSettings(): Flow<ReminderSettings>

    suspend fun saveSettings(settings: ReminderSettings): AppResult<Unit>
}
