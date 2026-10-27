package com.kitchenai.ui.presentation.shoppingwidget

/** What the home-screen widget draws. */
sealed interface ShoppingWidgetState {
    /** Signed out, or no kitchen or list yet: there is nothing to show but a way into the app. */
    data object NothingYet : ShoppingWidgetState

    data object Failed : ShoppingWidgetState

    /** Empty means the list exists and everything on it is ticked. */
    data class Lines(val lines: List<ShoppingWidgetLine>) : ShoppingWidgetState
}
