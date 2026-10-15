package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.UiText

/**
 * Everything the pantry screen draws.
 *
 * [error] never replaces [items]: a listener that fails after its first snapshot leaves the last
 * good list on screen with the failure above it, because a quiet stream is not an empty pantry.
 *
 * The option lists come from the catalogue: nothing here is a list this app wrote down.
 */
data class PantryUiState(
    val items: List<PantryItemUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val editing: PantryItemUi? = null,
    val isEditorOpen: Boolean = false,
    val ingredients: List<IngredientOption> = emptyList(),
    /** The ingredients touched last, newest first: the sheet's shortcut for the weekly shop. */
    val recent: List<IngredientOption> = emptyList(),
    val units: List<Pair<TermRef, String>> = emptyList(),
    val locations: List<Pair<TermRef, String>> = emptyList(),
    /** Bumped by every save that keeps the sheet open, so the form starts again from empty. */
    val editorRevision: Int = 0,
    /** The name just added by "save and add another"; null once the sheet closes. */
    val lastAdded: String? = null,
)
