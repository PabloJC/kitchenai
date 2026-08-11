package com.kitchenai.shared.domain.model

import kotlin.time.Instant

/**
 * A shopping list owned by one user. Synchronisation is across that user's own devices;
 * sharing a list with other people is post-MVP and would need a membership model.
 *
 * [labels] carries the caller-provided name per language tag. The domain never invents a
 * default name: a hardcoded one would be a contextual constant tied to a single market.
 */
data class ShoppingList(
    val id: ShoppingListId,
    val ownerId: UserId,
    val labels: Map<String, String>,
    val updatedAt: Instant,
)
