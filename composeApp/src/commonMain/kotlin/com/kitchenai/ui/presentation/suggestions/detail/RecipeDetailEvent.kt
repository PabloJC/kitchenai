package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.shared.domain.model.CookPlan
import com.kitchenai.ui.presentation.common.UiText

/** One-shot outcomes. State carries what a rotation must survive; these must not replay. */
sealed interface RecipeDetailEvent {
    /** Both counts: "added" alone hides that some lines were left off, and why. */
    data class AddedToList(val added: Int, val skipped: Int) : RecipeDetailEvent

    /** Carries what was taken, so the snackbar's undo can give exactly that back. */
    data class Cooked(val cooked: CookPlan) : RecipeDetailEvent

    data object CookUndone : RecipeDetailEvent

    data object Saved : RecipeDetailEvent

    /** Offers an undo, which saves the dish still on screen again. */
    data object Unsaved : RecipeDetailEvent

    data class Failed(val message: UiText) : RecipeDetailEvent
}
