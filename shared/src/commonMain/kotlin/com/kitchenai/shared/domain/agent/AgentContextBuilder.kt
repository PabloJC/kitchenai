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
 *
 * [languageTags] comes from the caller, not from [profile]: the language to answer *this*
 * request in is a fact about the device asking, and the stored profile is a fact about the
 * person that must not be silently overwritten by whatever locale happened to be active on
 * first launch (#131).
 */
object AgentContextBuilder {
    fun build(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        languageTags: List<String>,
        now: Instant,
    ): AgentContext =
        AgentContext(
            languageTags = languageTags,
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
            // A free-text holding has no catalogue id for the agent to reason about, so it is
            // dropped here — before the cap, so it never displaces a holding the agent could use.
            .mapNotNull { (item, freshness) -> item.ingredient?.let { id -> Triple(id, item.quantity, freshness) } }
            .take(options.maxPantryEntries)
            .map { (id, quantity, freshness) -> PantryEntry(id, quantity, freshness is Freshness.ExpiringSoon) }
}
