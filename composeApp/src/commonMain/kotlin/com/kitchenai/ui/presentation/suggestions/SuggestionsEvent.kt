package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.ui.presentation.common.UiText

/** One-shot, so a rotation does not replay it as if it had just happened. */
sealed interface SuggestionsEvent {
    data class Failed(val message: UiText) : SuggestionsEvent
}
