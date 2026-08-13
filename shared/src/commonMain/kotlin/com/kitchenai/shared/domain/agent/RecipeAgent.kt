package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AgentId

/**
 * Something that can answer a suggestion request. A port, so the domain owns the orchestration
 * and no vendor is named above `data`.
 *
 * The MVP ships one implementation. The seam exists so that the second one is a binding rather
 * than a rewrite of everything that calls it.
 */
interface RecipeAgent {
    val id: AgentId

    val capabilities: Set<AgentCapability>

    suspend fun suggest(context: AgentContext): AppResult<AgentSuggestions>
}
