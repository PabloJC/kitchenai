package com.kitchenai.ui.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Appearance
import com.kitchenai.shared.domain.usecase.profile.ObserveAppearanceUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveAppearanceUseCase
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_action
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Read by the app root to theme everything, and by the profile to change it; both see one stored value. */
class AppearanceViewModel(
    observe: ObserveAppearanceUseCase,
    private val save: SaveAppearanceUseCase,
) : ViewModel() {
    private val failure = MutableStateFlow<UiText?>(null)

    val state: StateFlow<AppearanceUiState> =
        combine(observe(), failure, ::AppearanceUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceUiState())

    fun choose(appearance: Appearance) {
        failure.value = null
        viewModelScope.launch {
            val result = save(appearance)
            if (result is AppResult.Failure) failure.value = result.error.describe(Res.string.error_unauthorized_action)
        }
    }
}
