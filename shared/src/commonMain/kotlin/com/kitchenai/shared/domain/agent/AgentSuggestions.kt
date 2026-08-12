package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.Recipe

/**
 * What one agent answered, before anything in it has been verified.
 *
 * [agentId] and [modelId] are provenance the orchestrator stamps onto every suggestion it
 * keeps: support has to know which agent and which model produced a bad one.
 */
data class AgentSuggestions(
    val agentId: AgentId,
    val modelId: String,
    val suggestions: List<Recipe>,
)
