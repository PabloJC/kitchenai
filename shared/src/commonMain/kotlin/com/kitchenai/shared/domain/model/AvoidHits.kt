package com.kitchenai.shared.domain.model

/** Where a dish runs into the household's Avoid set. Empty means it does not. */
data class AvoidHits(
    val terms: List<TermRef> = emptyList(),
    val ingredients: List<IngredientId> = emptyList(),
) {
    val isEmpty: Boolean get() = terms.isEmpty() && ingredients.isEmpty()
}
