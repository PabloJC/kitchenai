package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.UiText

/**
 * What the shopping screen shows. Two lists rather than one with a flag: the screen draws two
 * sections. A just-ticked line stays in [unchecked] until its settle delay ends, ticked in place.
 *
 * The records below are the parts of this one state and change together, which is why they
 * share a file.
 */
data class ShoppingUiState(
    val listName: String = "",
    val unchecked: List<ShoppingItemUi> = emptyList(),
    val checked: List<ShoppingItemUi> = emptyList(),
    val draft: ShoppingDraftUi = ShoppingDraftUi(),
    // The unit vocabulary as `ref to word`, for the add field and the amount editor.
    val units: List<Pair<TermRef, String>> = emptyList(),
    val isLoading: Boolean = true,
    // The list never arrived, which is a different screen from a list that arrived empty.
    val failedToLoad: Boolean = false,
    val error: UiText? = null,
    // The error is a listener's, so listening again may fix it; a refused write is not.
    val canRetry: Boolean = false,
    // [unchecked] in the shop's order. One untitled group when the catalogue has no shop sections.
    val toBuy: List<ShoppingSectionUi> = emptyList(),
    val grouped: Boolean = false,
) {
    /** A list that never loaded already offers Try again in its own empty state: one button per failure. */
    val bannerOffersRetry: Boolean get() = canRetry && !failedToLoad

    /** What a move takes, bare lines too. A just-ticked line still sits in `unchecked`, so both lists count. */
    val ticked: List<ShoppingItemUi> get() = unchecked.filter(ShoppingItemUi::checked) + checked

    val tickedCount: Int get() = ticked.size
}

/**
 * One rendered line. [fromCatalogue] is not decoration: a catalogue line merges with itself when
 * it is added twice and a free-text line never does, so the screen has to show which one it is.
 */
data class ShoppingItemUi(
    val id: ShoppingItemId,
    val label: String,
    val quantity: String?,
    // The parts behind [quantity], so the amount editor opens on what the line holds.
    val amount: Double? = null,
    val unit: TermRef? = null,
    val fromCatalogue: Boolean,
    val checked: Boolean,
    // The shop section of its ingredient, for grouping; null for free text and untagged lines.
    val section: TermRef? = null,
    // The title of the recipe it was added for, when a saved or stored recipe still has it. A
    // generated dish nobody kept has no title anywhere, and then nothing is shown, never its id.
    val forRecipe: String? = null,
)

/** One group of [ShoppingUiState.toBuy]. A null [title] is the lines no section claims. */
data class ShoppingSectionUi(
    val section: TermRef?,
    val title: String?,
    val lines: List<ShoppingItemUi>,
)

/**
 * The line being typed. [picked] set means the next add carries an identifier, not words.
 * [amount] null means no quantity; [revision] changes after an add so the amount field, which
 * keeps its raw text locally, starts empty again.
 */
data class ShoppingDraftUi(
    val text: String = "",
    val picked: IngredientSuggestion? = null,
    val suggestions: List<IngredientSuggestion> = emptyList(),
    val amount: Double? = null,
    val unit: TermRef? = null,
    val revision: Int = 0,
)

/** A catalogue entry offered while typing, already resolved into a word. */
data class IngredientSuggestion(
    val id: IngredientId,
    val label: String,
    /** Already on the list, unticked: picking it merges into that line rather than adding a second. */
    val onList: Boolean = false,
)
