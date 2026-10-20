package com.kitchenai.ui.presentation.onboarding

import com.kitchenai.shared.domain.model.IngredientId

data class StapleUi(
    val id: IngredientId,
    val label: String,
    val isChosen: Boolean,
)
