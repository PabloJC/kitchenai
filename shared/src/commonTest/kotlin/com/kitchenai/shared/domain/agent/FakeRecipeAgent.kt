package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.HouseholdContext
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.usecase.recipe.user
import kotlin.time.Instant

/**
 * A [RecipeAgent] that answers whatever it was built with and records what it was asked, so a
 * test can prove both that the walk stopped and what reached the context.
 */
class FakeRecipeAgent(
    rawId: String,
    private val answer: AppResult<AgentSuggestions>,
    override val capabilities: Set<AgentCapability> = setOf(AgentCapability.SUGGEST_FROM_PANTRY),
) : RecipeAgent {
    override val id: AgentId = agentId(rawId)

    var calls: Int = 0
        private set

    var received: AgentContext? = null
        private set

    override suspend fun suggest(context: AgentContext): AppResult<AgentSuggestions> {
        calls++
        received = context
        return answer
    }
}

// Fixtures. Every reference here is opaque: naming a diet or an ingredient in a fixture is the
// same mistake as naming it in code.
internal fun agentId(raw: String): AgentId = (AgentId.of(raw) as AppResult.Success).data

internal fun agentAnswer(
    agent: String,
    suggestions: List<Recipe>,
): AppResult<AgentSuggestions> = AppResult.Success(AgentSuggestions(agentId(agent), "model-$agent", suggestions))

internal fun profile(
    constraints: List<DietaryConstraint> = emptyList(),
    servings: Int = 2,
): UserProfile =
    UserProfile
        .newFor(user, listOf("xx"), Instant.fromEpochSeconds(0))
        .copy(household = HouseholdContext(servings), constraints = constraints)
