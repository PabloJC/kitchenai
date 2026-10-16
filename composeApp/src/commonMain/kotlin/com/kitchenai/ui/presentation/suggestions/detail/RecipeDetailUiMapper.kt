package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.shared.domain.model.PantryDeduction
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
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
        quantity = quantity?.let { held -> held.format(resolver) },
        optional = optional,
        // A free-text holding names itself, the same rule the pantry row already follows.
        candidates =
            candidates.map { holding ->
                CandidateUi(holding.id, holding.freeText.orEmpty(), holding.id in confirmed)
            },
    )

internal fun PantryDeduction.toUi(resolver: LabelResolver): DeductionUi =
    DeductionUi(
        // A holding names itself the way its pantry row does: free text first, then the catalogue.
        name = before.freeText ?: before.ingredient?.let { resolver.label(it) ?: it.value }.orEmpty(),
        taken = taken.format(resolver),
        left = after.quantity.takeIf { it.amount > 0.0 }?.format(resolver),
    )

private fun Quantity.format(resolver: LabelResolver): String =
    formatQuantity(amount, unit?.let { resolver.label(it, amount) }, resolver.decimalSeparator)
