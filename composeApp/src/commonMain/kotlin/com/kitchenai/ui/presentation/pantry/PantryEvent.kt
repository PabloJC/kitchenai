package com.kitchenai.ui.presentation.pantry

/** What happens once and must not be replayed on the next recomposition. */
sealed interface PantryEvent {
    /** [name] is already resolved: the snackbar names the row the undo would bring back. */
    data class ItemRemoved(val name: String) : PantryEvent

    data class SaveFailed(val message: String) : PantryEvent
}
