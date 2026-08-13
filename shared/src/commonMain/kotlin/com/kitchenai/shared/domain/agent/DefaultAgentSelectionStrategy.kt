package com.kitchenai.shared.domain.agent

/**
 * Registration order, filtered by capability.
 *
 * Deliberately this dumb: a scoring strategy with a single agent to score would be a design
 * defended by nothing, and the interface above is where a real one goes when there is one.
 */
class DefaultAgentSelectionStrategy : AgentSelectionStrategy {
    override fun select(
        capability: AgentCapability,
        agents: List<RecipeAgent>,
    ): List<RecipeAgent> = agents.filter { capability in it.capabilities }
}
