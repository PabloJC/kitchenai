package com.kitchenai.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Every destination in the app. The hierarchy stays one level deep on purpose: nesting is what
 * makes a sealed type expensive to read, both for the navigation serializer and for a reader.
 */
sealed interface Route {
    @Serializable
    data object Pantry : Route

    @Serializable
    data object ShoppingList : Route

    @Serializable
    data object Suggestions : Route

    @Serializable
    data class RecipeDetail(val recipeId: String) : Route

    @Serializable
    data object Profile : Route
}
