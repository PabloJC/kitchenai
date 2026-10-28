package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.ui.presentation.common.LabelResolver

/** The words the pantry shows an item by: its own text, or its catalogue name, or its id when unnamed. */
internal fun namesFrom(
    catalogue: List<Ingredient>,
    languageTags: List<String>,
): (PantryItem) -> String {
    val labels = LabelResolver(ingredients = catalogue, languageTags = languageTags)
    return { item -> item.freeText ?: item.ingredient?.let { id -> labels.label(id) ?: id.value }.orEmpty() }
}
