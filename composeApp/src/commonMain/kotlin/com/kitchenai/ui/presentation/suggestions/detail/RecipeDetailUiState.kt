package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.ui.presentation.common.UiText

/**
 * The detail screen's one state.
 *
 * The three ingredient buckets are separate fields rather than one list with a flag, because
 * the screen must never be able to render them as one: "cannot verify" is not "missing", and
 * a list that could merge them would eventually merge them.
 */
data class RecipeDetailUiState(
    val title: String = "",
    val summary: String? = null,
    val totalMinutes: Int? = null,
    val servings: Int = 1,
    val held: List<IngredientLineUi> = emptyList(),
    val missing: List<IngredientLineUi> = emptyList(),
    val unverifiable: List<IngredientLineUi> = emptyList(),
    val steps: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val isSaved: Boolean = false,
    val error: UiText? = null,
) {
    /**
     * Cooking is refused without every required line, so the button says so before it is tapped.
     * `title` standing in for "a recipe loaded": without it an empty screen reports nothing
     * missing, which is not the same as having everything.
     */
    val canCook: Boolean get() = !isLoading && !isWorking && title.isNotEmpty() && missing.isEmpty()
}

data class IngredientLineUi(
    val name: String,
    val quantity: String?,
    val optional: Boolean,
)
