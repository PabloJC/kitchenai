package com.kitchenai.shared.domain.agent

/**
 * Picks who gets asked, in the order they get asked.
 *
 * It returns a list and not one agent because that is what makes falling back to the next
 * candidate expressible instead of hardcoded in the orchestrator.
 */
fun interface AgentSelectionStrategy {
    fun select(
        capability: AgentCapability,
        agents: List<RecipeAgent>,
    ): List<RecipeAgent>
}
