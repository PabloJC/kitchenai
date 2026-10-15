package com.kitchenai.ui.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.ui.platform.NetworkMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Connectivity as state, for the shell's offline banner and for any screen that needs to disable an
 * action offline (the suggestions list, in a follow-up). It listens only while something collects it.
 */
class ConnectivityViewModel(
    monitor: NetworkMonitor,
) : ViewModel() {
    val state: StateFlow<ConnectivityUiState> =
        monitor.isOnline
            .map { online -> ConnectivityUiState(isOnline = online) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ConnectivityUiState())

    private companion object {
        // Rides out a configuration change without dropping the platform callback.
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
