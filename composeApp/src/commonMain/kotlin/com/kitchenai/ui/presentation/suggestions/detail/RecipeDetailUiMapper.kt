package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.ui.designsystem.format.formatQuantity
import com.kitchenai.ui.presentation.common.LabelResolver

internal fun RecipeIngredient.toUi(
    resolver: LabelResolver,
    candidates: List<PantryItem> = emptyList(),
    confirmed: Set<PantryItemId> = emptySet(),
): IngredientLineUi =
    IngredientLineUi(
        name = freeText ?: ingredient?.let { resolver.label(it) ?: it.value }.orEmpty(),
        quantity = quantity?.let { held -> formatQuantity(held.amount, held.unit?.let(resolver::label)) },
        optional = optional,
        // A free-text holding names itself, the same rule the pantry row already follows.
        candidates =
            candidates.map { holding ->
                CandidateUi(holding.id, holding.freeText.orEmpty(), holding.id in confirmed)
            },
    )
