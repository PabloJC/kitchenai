package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.ui.designsystem.format.formatQuantity
import com.kitchenai.ui.presentation.common.LabelResolver

internal fun RecipeIngredient.toUi(resolver: LabelResolver): IngredientLineUi =
    IngredientLineUi(
        name = freeText ?: ingredient?.let { resolver.label(it) ?: it.value }.orEmpty(),
        quantity = quantity?.let { held -> formatQuantity(held.amount, held.unit?.let(resolver::label)) },
        optional = optional,
    )
