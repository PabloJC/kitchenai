package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.ui.presentation.common.UiText

/**
 * What the shopping screen shows. Two lists rather than one with a flag: the screen draws two
 * sections, and the split is the ordering of the item stream made explicit.
 *
 * The four records below are the parts of this one state and change together, which is why they
 * share a file.
 */
data class ShoppingUiState(
    val listName: String = "",
    val unchecked: List<ShoppingItemUi> = emptyList(),
    val checked: List<ShoppingItemUi> = emptyList(),
    val draft: ShoppingDraftUi = ShoppingDraftUi(),
    val isLoading: Boolean = true,
    // The list never arrived, which is a different screen from a list that arrived empty.
    val failedToLoad: Boolean = false,
    val error: UiText? = null,
)

/**
 * One rendered line. [fromCatalogue] is not decoration: a catalogue line merges with itself when
 * it is added twice and a free-text line never does, so the screen has to show which one it is.
 */
data class ShoppingItemUi(
    val id: ShoppingItemId,
    val label: String,
    val quantity: String?,
    // No provenance field on purpose. The item carries a recipe id and nothing can turn it into
    // a title — a generated dish exists in no repository — so a line from a dish and a line
    // typed by hand are the same line here, and the screen must not invent a difference.
    val fromCatalogue: Boolean,
    val checked: Boolean,
)

/** The line being typed. [picked] set means the next add carries an identifier, not words. */
data class ShoppingDraftUi(
    val text: String = "",
    val picked: IngredientSuggestion? = null,
    val suggestions: List<IngredientSuggestion> = emptyList(),
)

/** A catalogue entry offered while typing, already resolved into a word. */
data class IngredientSuggestion(
    val id: IngredientId,
    val label: String,
)
