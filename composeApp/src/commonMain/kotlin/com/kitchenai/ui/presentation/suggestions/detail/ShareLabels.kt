package com.kitchenai.ui.presentation.suggestions.detail

/** The headings and words a shared recipe needs, resolved by the screen in the reader's language. */
data class ShareLabels(
    val servings: String,
    val minutes: String?,
    val ingredients: String,
    val steps: String,
    val optionalSuffix: String,
)
