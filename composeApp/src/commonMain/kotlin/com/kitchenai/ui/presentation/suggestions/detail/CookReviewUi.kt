package com.kitchenai.ui.presentation.suggestions.detail

/** The cook confirmation's content: each holding it changes, and what it leaves untouched. */
data class CookReviewUi(
    val deductions: List<DeductionUi>,
    /** Required lines the pantry lacks; a cook anyway goes ahead without them. */
    val missing: List<String>,
)
