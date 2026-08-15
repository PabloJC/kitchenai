package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.UiText

/**
 * Everything the pantry screen draws.
 *
 * [error] never replaces [items]: a listener that fails after its first snapshot leaves the last
 * good list on screen with the failure above it, because a quiet stream is not an empty pantry.
 *
 * The three option lists are pairs of identifier to label, and every one of them comes from the
 * catalogue: nothing here is a list this app wrote down.
 */
data class PantryUiState(
    val items: List<PantryItemUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val editing: PantryItemUi? = null,
    val isEditorOpen: Boolean = false,
    val ingredients: List<Pair<IngredientId, String>> = emptyList(),
    val units: List<Pair<TermRef, String>> = emptyList(),
    val locations: List<Pair<TermRef, String>> = emptyList(),
)
