package com.kitchenai.ui.presentation.shoppingwidget

import com.kitchenai.shared.domain.model.ShoppingItemId

/** One unchecked line, already in words: the widget has no catalogue of its own to resolve it with. */
data class ShoppingWidgetLine(
    val id: ShoppingItemId,
    val label: String,
)
