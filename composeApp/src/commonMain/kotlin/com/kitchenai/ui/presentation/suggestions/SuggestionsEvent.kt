package com.kitchenai.ui.presentation.suggestions

/** One-shot, so a rotation does not replay it as if it had just happened. */
sealed interface SuggestionsEvent {
    data class Failed(val message: String) : SuggestionsEvent
}
