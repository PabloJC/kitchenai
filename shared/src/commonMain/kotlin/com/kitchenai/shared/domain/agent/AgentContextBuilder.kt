package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.domain.model.Freshness
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.model.freshnessOn
import kotlinx.datetime.TimeZone
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
        zone: TimeZone = TimeZone.UTC,
    ): AgentContext =
        AgentContext(
            languageTags = languageTags,
            servings = profile.household.servings,
            constraints = profile.constraints,
            preferences = profile.preferences,
            avoidedIngredients = profile.avoidedIngredients,
            pantry = entries(pantry, options, now, zone),
            options = options,
            freeTextPantry = freeTextEntries(pantry, options, now, zone),
        )

    /**
     * Expired stock is not stock, and what expires first is what the answer should use, so the
     * cap cuts the tail rather than an arbitrary slice.
     */
    private fun entries(
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        now: Instant,
        zone: TimeZone,
    ): List<PantryEntry> =
        usable(pantry, options, now, zone)
            // Free-text holdings travel in their own capped list, so they never displace one of these.
            .mapNotNull { (item, freshness) -> item.ingredient?.let { id -> Triple(id, item.quantity, freshness) } }
            .take(options.maxPantryEntries)
            .map { (id, quantity, freshness) -> PantryEntry(id, quantity, freshness is Freshness.ExpiringSoon) }

    private fun freeTextEntries(
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        now: Instant,
        zone: TimeZone,
    ): List<PantryFreeTextEntry> =
        usable(pantry, options, now, zone)
            .filter { (item, _) -> item.ingredient == null }
            .mapNotNull { (item, freshness) ->
                item.freeText?.let(::foodName)?.let { name ->
                    PantryFreeTextEntry(name, item.quantity, freshness is Freshness.ExpiringSoon)
                }
            }.take(options.maxFreeTextEntries)

    private fun usable(
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        now: Instant,
        zone: TimeZone,
    ): List<Pair<PantryItem, Freshness>> =
        pantry
            .map { it to it.freshnessOn(now, zone, options.expiringSoonDays) }
            .filterNot { (_, freshness) -> freshness == Freshness.Expired }
            .sortedBy { (item, _) -> item.expiresAt ?: Instant.DISTANT_FUTURE }

    /**
     * A name, not a sentence: letters, marks, digits, spaces, `'` and `-`, at most [MAX_FOOD_NAME_LENGTH].
     * The function refuses anything else, so what it would refuse is cleaned here instead.
     */
    internal fun foodName(raw: String): String? =
        raw
            .map { if (it.isFoodNameChar()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .take(MAX_FOOD_NAME_LENGTH)
            .trimEnd()
            .takeIf { name -> name.any { it.isLetter() } }

    /** Mirrors `MAX_FOOD_NAME` in `functions/src/contract.ts`. */
    internal const val MAX_FOOD_NAME_LENGTH: Int = 40
}

// Marks included: without them a Devanagari or Thai name would be cut into pieces.
private fun Char.isFoodNameChar(): Boolean =
    isLetterOrDigit() ||
        this == '\'' ||
        this == '-' ||
        category == CharCategory.NON_SPACING_MARK ||
        category == CharCategory.COMBINING_SPACING_MARK ||
        category == CharCategory.ENCLOSING_MARK
