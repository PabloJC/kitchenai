package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.ui.presentation.common.UiText

/** What happens once and must not be replayed on the next recomposition. */
sealed interface PantryEvent {
    /**
     * [name] is already resolved: the snackbar names the row the undo would bring back, and
     * [restore] is that row. Carrying it means a second removal cannot steal the first one's
     * undo — swiping twice quickly leaves two offers, each restoring its own row.
     */
    data class ItemRemoved(
        val name: String,
        val restore: PantryItem,
    ) : PantryEvent

    data class SaveFailed(val message: UiText) : PantryEvent

    /** [name] is the row put on the list, for the confirmation that offers to go there. */
    data class AddedToList(val name: String) : PantryEvent

    /** Every row Remove all took, so one Undo puts all of them back. */
    data class ExpiredRemoved(val restore: List<PantryItem>) : PantryEvent
}
