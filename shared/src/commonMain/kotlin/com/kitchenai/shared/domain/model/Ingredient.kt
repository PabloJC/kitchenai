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
    /** True when this is sold as whole items (an onion, a bulb of garlic): never a fraction. */
    val purchasedWhole: Boolean = false,
    /** The catalogue's own picture (#401), a Storage download URL; null when it has none. */
    val imageUrl: String? = null,
    /** Retired by the admin (#403): never offered for something new, still resolved for what uses it. */
    val archived: Boolean = false,
)
