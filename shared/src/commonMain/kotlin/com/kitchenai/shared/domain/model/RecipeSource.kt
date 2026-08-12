package com.kitchenai.shared.domain.model

import kotlin.time.Instant

/**
 * Where a recipe came from.
 *
 * Provenance is not decoration: the UI has to mark generated content, and support has to know
 * which agent and which model produced a bad suggestion.
 */
sealed interface RecipeSource {
    data object Catalogue : RecipeSource

    data class Agent(
        val agentId: AgentId,
        val modelId: String,
        val generatedAt: Instant,
    ) : RecipeSource
}
