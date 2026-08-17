package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.service.PantryMatcher
import kotlin.time.Instant

/**
 * Pure orchestration: build the context, walk the candidates, verify the answer.
 *
 * Nothing an agent says about coverage or provenance survives this class — both are recomputed
 * here, which is the whole reason the model proposing and the domain verifying are separate.
 */
class DefaultAgentOrchestrator(
    private val registry: AgentRegistry,
    private val selection: AgentSelectionStrategy,
    private val time: TimeProvider,
) : AgentOrchestrator {
    override suspend fun suggest(
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        languageTags: List<String>,
    ): AppResult<List<RecipeSuggestion>> {
        val candidates = selection.select(AgentCapability.SUGGEST_FROM_PANTRY, registry.agents())
        if (candidates.isEmpty()) return AppResult.Failure(AppError.NotFound("agent"))
        val now = time.now()
        val context = AgentContextBuilder.build(profile, pantry, options, languageTags, now)
        return ask(candidates, context).map { answer -> verify(answer, profile, pantry, options, now) }
    }

    /**
     * The first success wins. [AppError.Unauthorized] aborts instead: a rejected credential is
     * not a reason to fan the same request out to everyone else.
     */
    private suspend fun ask(
        candidates: List<RecipeAgent>,
        context: AgentContext,
    ): AppResult<AgentSuggestions> {
        var last: AppError = AppError.NotFound("agent")
        for (agent in candidates) {
            when (val answer = agent.suggest(context)) {
                is AppResult.Success -> return answer
                is AppResult.Failure -> {
                    if (answer.error is AppError.Unauthorized) return answer
                    last = answer.error
                }
            }
        }
        return AppResult.Failure(last)
    }

    /**
     * The second gate on excluded terms, and the one under test here — the server filters too,
     * and neither side is trusted to be the only one that did.
     */
    private fun verify(
        answer: AgentSuggestions,
        profile: UserProfile,
        pantry: List<PantryItem>,
        options: SuggestionOptions,
        now: Instant,
    ): List<RecipeSuggestion> {
        val excluded = profile.constraints.filter { it.strength == ConstraintStrength.EXCLUDE }.map { it.term }.toSet()
        return answer.suggestions
            .filterNot { it.violates(excluded) }
            .take(options.maxResults)
            .map { recipe ->
                RecipeSuggestion(
                    recipe = recipe,
                    match = PantryMatcher.match(recipe, pantry, now),
                    source = RecipeSource.Agent(answer.agentId, answer.modelId, now),
                )
            }
    }
}

/** Dropped silently: what a rejected suggestion contained is not something worth logging. */
private fun Recipe.violates(excluded: Set<TermRef>): Boolean = tags.any { it in excluded }
