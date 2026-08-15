package com.kitchenai.ui.presentation.suggestions

/** One-shot outcomes. State carries what a rotation must survive; these must not replay. */
sealed interface RecipeDetailEvent {
    /** Both counts: "added" alone hides that some lines were left off, and why. */
    data class AddedToList(val added: Int, val skipped: Int) : RecipeDetailEvent

    data object Cooked : RecipeDetailEvent

    data object Saved : RecipeDetailEvent

    data class Failed(val message: String) : RecipeDetailEvent
}
