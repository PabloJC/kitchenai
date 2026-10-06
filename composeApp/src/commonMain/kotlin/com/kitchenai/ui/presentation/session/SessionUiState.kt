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

    /** A uid change after [Ready] failed; the user is signed in, so retry follows the session instead of restarting. */
    data class SwitchFailed(override val message: UiText) : Failure
}
