package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.ui.presentation.common.UiText

/** One-shot, so a rotation does not replay it as if it had just happened. */
sealed interface SuggestionsEvent {
    /** Both counts: "added" alone hides that some lines were left off. */
    data class AddedToList(val added: Int, val skipped: Int) : SuggestionsEvent

    data class Failed(val message: UiText) : SuggestionsEvent
}
