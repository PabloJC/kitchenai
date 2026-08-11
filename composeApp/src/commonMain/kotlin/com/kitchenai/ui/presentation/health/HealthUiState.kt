package com.kitchenai.ui.presentation.health

sealed interface HealthUiState {
    data object Loading : HealthUiState

    data class Ready(val projectId: String) : HealthUiState

    data class Error(val message: String) : HealthUiState
}
