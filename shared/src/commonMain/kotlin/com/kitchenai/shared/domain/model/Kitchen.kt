package com.kitchenai.shared.domain.model

/**
 * A group of users sharing one pantry, shopping list and saved recipes.
 *
 * Named `Kitchen`, not `Household`: [UserProfile.household] already holds a [HouseholdContext] —
 * servings, budget, cooking-minutes defaults — a per-profile number, unrelated to this group.
 *
 * [memberDisplayNames] is denormalised here rather than read cross-user from [UserProfile], so
 * `users/{uid}` stays owner-only readable. Keyed by [UserId.value], not [UserId] itself: every
 * other public model's map towards iOS is keyed by plain `String` (`Ingredient.labels`,
 * `Taxonomy.labels`, `Term.labels`), and a `@JvmInline value class` key would have made this the
 * first exception, on a type this contract's own `Flow` exposes straight across that boundary.
 */
data class Kitchen(
    val id: KitchenId,
    val ownerId: UserId,
    val memberIds: Set<UserId>,
    val joinCode: KitchenJoinCode,
    val memberDisplayNames: Map<String, String>,
)
