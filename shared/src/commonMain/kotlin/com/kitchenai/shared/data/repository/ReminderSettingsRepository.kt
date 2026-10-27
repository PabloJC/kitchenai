package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.data.local.ReminderSettingsLocalDataSource
import com.kitchenai.shared.data.local.localCall
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.port.ReminderSettingsRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stored as `enabled;hour;minute;offered`. Anything this build cannot read falls back to the
 * defaults, which re-offer the reminder once: asking again beats never reminding.
 */
class ReminderSettingsRepository(
    private val local: ReminderSettingsLocalDataSource,
    private val dispatchers: DispatcherProvider,
) : ReminderSettingsRepositoryContract {
    override fun observeSettings(): Flow<ReminderSettings> =
        local.stored.map {
                raw ->
            raw?.let(::decode) ?: ReminderSettings()
        }

    override suspend fun saveSettings(settings: ReminderSettings): AppResult<Unit> =
        localCall(dispatchers) { local.write(encode(settings)) }

    private fun encode(settings: ReminderSettings): String =
        listOf(settings.enabled, settings.hour, settings.minute, settings.offered).joinToString(SEPARATOR)

    private fun decode(raw: String): ReminderSettings? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != FIELDS) return null
        val time = parts[1].toIntOrNull()?.let { hour -> parts[2].toIntOrNull()?.let { minute -> hour to minute } }
        val flags =
            parts[0].toBooleanStrictOrNull()?.let { enabled ->
                parts[3].toBooleanStrictOrNull()?.let { offered -> enabled to offered }
            }
        return if (time == null || flags == null) {
            null
        } else {
            ReminderSettings(enabled = flags.first, hour = time.first, minute = time.second, offered = flags.second)
        }
    }

    private companion object {
        const val SEPARATOR = ";"
        const val FIELDS = 4
    }
}
