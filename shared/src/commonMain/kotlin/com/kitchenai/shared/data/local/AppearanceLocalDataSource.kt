package com.kitchenai.shared.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The appearance as the store keeps it, a plain string; what it means is the repository's
 * business. The store has no change listener of its own, so writes go through here to be seen.
 */
class AppearanceLocalDataSource(
    private val store: PreferenceStore,
) {
    private val current = MutableStateFlow(store.read(KEY))
    val stored: StateFlow<String?> = current.asStateFlow()

    fun write(value: String) {
        store.write(KEY, value)
        current.value = value
    }

    private companion object {
        const val KEY = "appearance"
    }
}
