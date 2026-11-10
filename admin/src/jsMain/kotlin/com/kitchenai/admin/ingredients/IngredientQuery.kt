package com.kitchenai.admin.ingredients

import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.TermRef

data class IngredientQuery(
    val text: String = "",
    val tag: TermRef? = null,
    val archived: ArchivedFilter = ArchivedFilter.ACTIVE,
)

/** By id, so the order does not depend on which language the admin reads. */
fun List<Ingredient>.matching(query: IngredientQuery): List<Ingredient> {
    val needle = query.text.trim().lowercase()
    return filter { ingredient ->
        val shown =
            when (query.archived) {
                ArchivedFilter.ACTIVE -> !ingredient.archived
                ArchivedFilter.ARCHIVED -> ingredient.archived
                ArchivedFilter.ALL -> true
            }
        val named =
            needle.isEmpty() ||
                ingredient.id.value.lowercase().contains(needle) ||
                ingredient.labels.values.any { label -> label.lowercase().contains(needle) }
        shown && named && (query.tag == null || query.tag in ingredient.tags)
    }.sortedBy { ingredient -> ingredient.id.value }
}
