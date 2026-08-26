package com.kitchenai.shared.domain.model

import kotlin.time.Instant

/**
 * Everything the app knows about the person cooking: references and numbers, never words.
 *
 * This is what gets handed to the agent later, so anything that would tie it to one market
 * has to stay a [TermRef] resolved against a catalogue.
 */
data class UserProfile(
    val userId: UserId,
    val displayName: String?,
    val languageTags: List<String>,
    val household: HouseholdContext,
    val constraints: List<DietaryConstraint>,
    val preferences: List<TermRef>,
    val avoidedIngredients: List<IngredientId>,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * Empty everywhere and one serving: every other default would be a cultural guess,
         * and the language tags come from the device rather than from a chosen list.
         *
         * The profile screen has no control to change [HouseholdContext.servings] (#135): every
         * suggestion request carries this seeded value, not a stated household size.
         */
        fun newFor(
            userId: UserId,
            languageTags: List<String>,
            now: Instant,
        ): UserProfile =
            UserProfile(
                userId = userId,
                displayName = null,
                languageTags = languageTags,
                household = HouseholdContext(servings = 1),
                constraints = emptyList(),
                preferences = emptyList(),
                avoidedIngredients = emptyList(),
                updatedAt = now,
            )
    }
}
