package com.kitchenai.shared.domain.model

/**
 * A catalogue entry, read-only for the client.
 *
 * [labels] are display strings keyed by language tag, and [tags] are opaque [TermRef]s:
 * naming an ingredient or a food group in code would turn adding one into a release.
 */
data class Ingredient(
    val id: IngredientId,
    val labels: Map<String, String>,
    val defaultUnit: TermRef?,
    val tags: List<TermRef>,
)
