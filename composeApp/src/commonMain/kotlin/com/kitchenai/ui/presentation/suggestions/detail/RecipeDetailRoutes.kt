package com.kitchenai.ui.presentation.suggestions.detail

/** Where recipe detail can send the reader; the nav graph supplies them, the screen never navigates itself. */
data class RecipeDetailRoutes(
    val back: () -> Unit,
    val startCooking: () -> Unit,
    val shoppingList: () -> Unit,
)
