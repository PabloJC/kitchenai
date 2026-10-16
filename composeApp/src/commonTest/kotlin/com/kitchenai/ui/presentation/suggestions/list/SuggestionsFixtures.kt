package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.MissingIngredient
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.RecipeSuggestion

/** A dish whose one line is a catalogue ingredient the pantry does not cover. */
internal fun shortOfRice(): RecipeSuggestion {
    val line = RecipeIngredient(IngredientId.of("rice").orFail(), null, null, false)
    val recipe =
        Recipe(
            id = RecipeId.of("recipe-1").orFail(),
            title = "Dish",
            summary = null,
            servings = 2,
            totalMinutes = 20,
            ingredients = listOf(line),
            steps = listOf("Step"),
            tags = emptyList(),
            source = RecipeSource.Catalogue,
        )
    return RecipeSuggestion(
        recipe = recipe,
        match = PantryMatch(recipe.id, emptyList(), listOf(MissingIngredient(line, null)), emptyList()),
        source = RecipeSource.Catalogue,
    )
}

internal fun dish(
    id: String,
    source: RecipeSource = RecipeSource.Catalogue,
): Recipe = suggestion(id, source).recipe

internal fun suggestion(
    id: String,
    source: RecipeSource = RecipeSource.Catalogue,
): RecipeSuggestion {
    val recipe =
        Recipe(
            id = RecipeId.of(id).orFail(),
            title = "Dish",
            summary = null,
            servings = 2,
            totalMinutes = 20,
            ingredients = listOf(RecipeIngredient(null, "salt", null, false)),
            steps = listOf("Step"),
            tags = emptyList(),
            source = source,
        )
    return RecipeSuggestion(
        recipe = recipe,
        match = PantryMatch(recipe.id, emptyList(), emptyList(), recipe.ingredients),
        source = source,
    )
}

internal fun <T> AppResult<T>.orFail(): T = (this as AppResult.Success).data
