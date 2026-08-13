package com.kitchenai.shared.domain.model

/**
 * One proposal as the app is allowed to show it: the dish, what the pantry covers of it, and
 * where it came from.
 *
 * [match] and [source] are computed and stamped by the orchestrator. Neither is ever the
 * agent's own claim about itself.
 */
data class RecipeSuggestion(
    val recipe: Recipe,
    val match: PantryMatch,
    val source: RecipeSource,
)
