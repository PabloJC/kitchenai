package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.domain.model.RecipeId

/** What a card's taps do, the same for the latest ideas and an earlier set. */
internal class SuggestionCardActions(
    val onOpen: (RecipeId) -> Unit,
    val onAddMissing: (RecipeId) -> Unit,
    val onToggleSaved: (RecipeId) -> Unit,
)
