package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TermRef

/** A catalogue entry as the add sheet offers it: [label] is already in the user's language. */
data class IngredientOption(
    val id: IngredientId,
    val label: String,
    val defaultUnit: TermRef?,
)
