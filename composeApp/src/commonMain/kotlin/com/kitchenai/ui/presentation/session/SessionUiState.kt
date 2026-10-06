package com.kitchenai.ui.presentation.session

import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.UiText

/** What the gate knows about the session. Nothing below it composes before [Ready]. */
sealed interface SessionUiState {
    data object Loading : SessionUiState

    data class Ready(val userId: UserId) : SessionUiState

    /** What the gate draws, and retries, for either kind of failure. */
    sealed interface Failure : SessionUiState {
        val message: UiText
    }

    data class Failed(override val message: UiText) : Failure

    /** The uid changed after [Ready] and its setup failed; the user is signed in, so retry never restarts. */
    data class SwitchFailed(override val message: UiText) : Failure
}
