package com.kitchenai.ui.presentation.suggestions.detail

/** "Rice: −200 g, 300 g left". [left] is null when the cook uses the holding up. */
data class DeductionUi(
    val name: String,
    val taken: String,
    val left: String?,
)
