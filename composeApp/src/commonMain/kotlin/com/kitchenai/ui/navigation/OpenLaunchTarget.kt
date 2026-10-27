package com.kitchenai.ui.navigation

import androidx.navigation.NavHostController

/** Each target is a tab; adding also focuses the Shopping field once that screen is up. */
internal fun NavHostController.open(
    target: LaunchTarget,
    addRequests: ShoppingAddRequests,
) {
    when (target) {
        LaunchTarget.PANTRY -> switchTo(Route.Pantry)
        LaunchTarget.IDEAS -> switchTo(Route.SuggestionsGraph)
        LaunchTarget.SHOPPING_ADD -> {
            switchTo(Route.ShoppingList)
            addRequests.request()
        }
    }
}
