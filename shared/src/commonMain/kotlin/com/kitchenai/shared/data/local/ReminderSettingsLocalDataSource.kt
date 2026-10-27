package com.kitchenai.shared.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The reminder settings as the store keeps them, one encoded string; reading it is the repository's job. */
class ReminderSettingsLocalDataSource(
    private val store: PreferenceStore,
) {
    private val current = MutableStateFlow(store.read(KEY))
    val stored: StateFlow<String?> = current.asStateFlow()

    fun write(value: String) {
        store.write(KEY, value)
        current.value = value
    }

    private companion object {
        const val KEY = "reminders.expiry"
    }
}
