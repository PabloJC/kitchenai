package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.domain.model.Freshness
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.model.freshnessAt
import kotlin.time.Instant

/**
 * Turns what is stored about a user into the request an agent receives.
 *
 * Pure, synchronous and total: [now] is a parameter rather than a clock, so what an agent is
 * sent is reproducible from the same profile and the same pantry.
 */
object AgentContextBuilder {
    fun build(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        now: Instant,
    ): AgentContext =
        AgentContext(
            languageTags = profile.languageTags,
            servings = profile.household.servings,
            constraints = profile.constraints,
            preferences = profile.preferences,
            avoidedIngredients = profile.avoidedIngredients,
            pantry = entries(pantry, options, now),
            options = options,
        )

    /**
     * Expired stock is not stock, and what expires first is what the answer should use, so the
     * cap cuts the tail rather than an arbitrary slice.
     */
    private fun entries(
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        now: Instant,
    ): List<PantryEntry> =
        pantry
            .map { it to it.freshnessAt(now, options.expiringSoonWindow) }
            .filterNot { (_, freshness) -> freshness == Freshness.Expired }
            .sortedBy { (item, _) -> item.expiresAt ?: Instant.DISTANT_FUTURE }
            .take(options.maxPantryEntries)
            .map { (item, freshness) ->
                PantryEntry(item.ingredient, item.quantity, freshness is Freshness.ExpiringSoon)
            }
}
