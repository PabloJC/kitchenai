package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TermRef

/**
 * Everything an agent is told about this request: identifiers and numbers, plus the bounded
 * names in [freeTextPantry] (#393).
 *
 * There is no instruction, no system message and no word describing the cook here on purpose.
 * Turning this into a prompt is the server's job, so the client cannot be where one is assembled.
 */
data class AgentContext(
    val languageTags: List<String>,
    val servings: Int,
    val constraints: List<DietaryConstraint>,
    val preferences: List<TermRef>,
    val avoidedIngredients: List<IngredientId>,
    val pantry: List<PantryEntry>,
    val options: SuggestionOptions,
    val freeTextPantry: List<PantryFreeTextEntry> = emptyList(),
)

/**
 * One holding as the agent sees it: what it is, how much of it, and whether it is about to be
 * lost. No label, no storage place and no note the user wrote.
 */
data class PantryEntry(
    val ingredient: IngredientId,
    val quantity: Quantity,
    val expiring: Boolean,
)
