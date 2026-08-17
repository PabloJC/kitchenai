package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.ui.designsystem.format.formatQuantity
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.wordFor

internal fun ShoppingItem.toUi(labels: LabelResolver): ShoppingItemUi =
    ShoppingItemUi(
        id = id,
        label = label(labels),
        quantity = quantity?.render(labels),
        fromCatalogue = ingredient != null,
        checked = checked,
    )

/** A catalogue miss renders the identifier: ugly and honest beats a placeholder that hides it. */
internal fun ShoppingItem.label(labels: LabelResolver): String =
    freeText ?: ingredient?.let { id -> labels.label(id) ?: id.value }.orEmpty()

private fun Quantity.render(labels: LabelResolver): String =
    formatQuantity(amount, unit?.let { ref -> labels.wordFor(ref) })
