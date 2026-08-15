package com.kitchenai.ui.presentation.session

import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.UiText

/** What the gate knows about the session. Nothing below it composes before [Ready]. */
sealed interface SessionUiState {
    data object Loading : SessionUiState

    data class Ready(val userId: UserId) : SessionUiState

    data class Failed(val message: UiText) : SessionUiState
}
