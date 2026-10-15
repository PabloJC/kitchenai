package com.kitchenai.ui.presentation.common

import com.kitchenai.ui.platform.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Connectivity a test flips by hand; online until told otherwise, like the real monitor's unknown. */
class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {
    private val online = MutableStateFlow(initiallyOnline)

    override val isOnline: Flow<Boolean> = online

    fun setOnline(value: Boolean) {
        online.value = value
    }
}
