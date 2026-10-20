package com.kitchenai.shared.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The first-run flag as the store keeps it. The store has no change listener, so writes go through here. */
class OnboardingLocalDataSource(
    private val store: PreferenceStore,
) {
    private val current = MutableStateFlow(store.read(KEY))
    val stored: StateFlow<String?> = current.asStateFlow()

    fun write(value: String) {
        store.write(KEY, value)
        current.value = value
    }

    private companion object {
        const val KEY = "onboarding.completed"
    }
}
