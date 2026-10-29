package com.kitchenai.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Every destination in the app. The hierarchy stays one level deep on purpose: nesting is what
 * makes a sealed type expensive to read, both for the navigation serializer and for a reader.
 * The `*Graph` routes are not screens: they wrap a tab with the screens opened from it, so the
 * bar can keep that tab selected.
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

    /** Always opened over [RecipeDetail], whose ViewModel it shares. */
    @Serializable
    data class CookingMode(val recipeId: String) : Route

    @Serializable
    data object Profile : Route

    @Serializable
    data object Kitchen : Route

    @Serializable
    data object PhotoCredits : Route

    @Serializable
    data object Licences : Route

    @Serializable
    data object SuggestionsGraph : Route

    @Serializable
    data object ProfileGraph : Route
}
