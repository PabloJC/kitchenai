package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.testDispatchers
import com.kitchenai.shared.data.local.PreferenceStore
import com.kitchenai.shared.data.local.ReminderSettingsLocalDataSource
import com.kitchenai.shared.domain.model.ReminderSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSettingsRepositoryTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `nothing stored reads as the defaults`() =
        runTest(dispatcher) {
            assertEquals(ReminderSettings(), repository(SettingsStore()).observeSettings().first())
        }

    @Test
    fun `saved settings survive the next launch`() =
        runTest(dispatcher) {
            val store = SettingsStore()
            val chosen = ReminderSettings(enabled = false, hour = 7, minute = 45, offered = true)

            assertTrue(repository(store).saveSettings(chosen) is AppResult.Success)

            assertEquals(chosen, repository(store).observeSettings().first())
        }

    @Test
    fun `a value this build cannot read falls back to the defaults`() =
        runTest(dispatcher) {
            val store = SettingsStore("reminders.expiry" to "yes;at six")

            assertEquals(ReminderSettings(), repository(store).observeSettings().first())
        }

    @Test
    fun `a store that throws on write is reported rather than crashing`() =
        runTest(dispatcher) {
            val broken =
                object : PreferenceStore {
                    override fun read(key: String): String? = null

                    override fun write(
                        key: String,
                        value: String,
                    ): Unit = error("disk full")
                }

            assertTrue(repository(broken).saveSettings(ReminderSettings()) is AppResult.Failure)
        }

    private fun repository(store: PreferenceStore): ReminderSettingsRepository =
        ReminderSettingsRepository(ReminderSettingsLocalDataSource(store), testDispatchers(dispatcher))
}

private class SettingsStore(vararg initial: Pair<String, String>) : PreferenceStore {
    private val values = mutableMapOf(*initial)

    override fun read(key: String): String? = values[key]

    override fun write(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
