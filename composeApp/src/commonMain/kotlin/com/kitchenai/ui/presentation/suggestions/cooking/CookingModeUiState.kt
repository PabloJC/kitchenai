package com.kitchenai.ui.presentation.suggestions.cooking

/**
 * What cooking mode adds on top of the recipe detail it is opened from: the recipe, its servings
 * and the cook action stay with that screen's ViewModel, so the two can never disagree.
 */
data class CookingModeUiState(
    /** Positions in the screen's ingredient list, which keeps its order while the screen is open. */
    val ticked: Set<Int> = emptySet(),
    /** This recipe's timers, by step; finished ones stay until dismissed. */
    val timers: List<StepTimerUi> = emptyList(),
)
