package com.kitchenai.shared.domain.model

import kotlin.time.Instant

/**
 * A shopping list owned by one kitchen. Synchronisation is across every member of it; sharing a
 * list with people outside the kitchen is not something this model expresses.
 *
 * [labels] carries the caller-provided name per language tag. The domain never invents a
 * default name: a hardcoded one would be a contextual constant tied to a single market.
 */
data class ShoppingList(
    val id: ShoppingListId,
    val kitchenId: KitchenId,
    val labels: Map<String, String>,
    val updatedAt: Instant,
)
