package com.kitchenai.ui.presentation.common

/** Optimistic until the monitor answers: a banner flashed at launch would be wrong more often than right. */
data class ConnectivityUiState(
    val isOnline: Boolean = true,
)
