package com.kitchenai.ui.presentation.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.usecase.CheckFirebaseHealth
import com.kitchenai.shared.domain.usecase.NoParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HealthViewModel(
    private val checkFirebaseHealth: CheckFirebaseHealth,
) : ViewModel() {
    private val _state = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    init {
        check()
    }

    private fun check() {
        viewModelScope.launch {
            _state.value =
                when (val result = checkFirebaseHealth(NoParams)) {
                    is AppResult.Success -> HealthUiState.Ready(result.data)
                    is AppResult.Failure -> HealthUiState.Error(result.error.describe())
                }
        }
    }
}

/** The cause is dropped on purpose: it can carry paths and identifiers, and this ends up in screenshots. */
private fun AppError.describe(): String =
    when (this) {
        is AppError.Network -> "No connection to Firebase"
        is AppError.Unauthorized -> "Firebase rejected the credentials"
        is AppError.NotFound -> "Cannot find $resource"
        is AppError.Validation -> "Invalid $field: $reason"
        is AppError.Unknown -> "Firebase did not start"
    }
