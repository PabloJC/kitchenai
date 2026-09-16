package com.kitchenai.shared.domain.model

/**
 * A group of users sharing one pantry, shopping list and saved recipes.
 *
 * Named `Kitchen`, not `Household`: [UserProfile.household] already holds a [HouseholdContext] —
 * servings, budget, cooking-minutes defaults — a per-profile number, unrelated to this group.
 *
 * [memberDisplayNames] is denormalised here rather than read cross-user from [UserProfile], so
 * `users/{uid}` stays owner-only readable.
 */
data class Kitchen(
    val id: KitchenId,
    val ownerId: UserId,
    val memberIds: Set<UserId>,
    val joinCode: KitchenJoinCode,
    val memberDisplayNames: Map<UserId, String>,
)
