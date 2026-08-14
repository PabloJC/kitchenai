package com.kitchenai.shared.data.remote.agent

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.data.remote.agent.dto.ConstraintDto
import com.kitchenai.shared.data.remote.agent.dto.PantryEntryDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestOptionsDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestRequestDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestResponseDto
import com.kitchenai.shared.data.remote.agent.dto.TermRefDto
import com.kitchenai.shared.data.remote.firebase.toAppError
import com.kitchenai.shared.domain.agent.AgentCapability
import com.kitchenai.shared.domain.agent.AgentContext
import com.kitchenai.shared.domain.agent.AgentSuggestions
import com.kitchenai.shared.domain.agent.PantryEntry
import com.kitchenai.shared.domain.agent.RecipeAgent
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.port.IdGenerator
import kotlinx.coroutines.withContext

/** The deployed callable. Not a secret, and the only remote identifier in this module. */
private const val SUGGEST_FUNCTION = "suggestRecipes"

/**
 * The one place the product talks to a model, and it does so through a function that holds the
 * credential. No key, no endpoint and no provider name lives in this binary.
 *
 * [id] and [capabilities] are constructor parameters so that pointing a second agent at a
 * different function is a Koin line rather than a class.
 */
internal class CallableFunctionRecipeAgent(
    override val id: AgentId,
    override val capabilities: Set<AgentCapability>,
    private val transport: CallableTransport,
    private val validator: SuggestionValidator,
    private val ids: IdGenerator,
    private val dispatchers: DispatcherProvider,
) : RecipeAgent {
    override suspend fun suggest(context: AgentContext): AppResult<AgentSuggestions> =
        withContext(dispatchers.io) {
            decode(context).flatMap { response -> validator.validate(response, context.options.maxResults) }
        }

    /**
     * Both the call and its decoding are caught here: a body that is not the shape we asked for
     * is the same class of problem as a call that never arrived, and neither may escape as an
     * exception.
     */
    private suspend fun decode(context: AgentContext): AppResult<SuggestResponseDto> =
        runCatching { transport.call(SUGGEST_FUNCTION, context.toRequest()) }.fold(
            onSuccess = { decoded -> AppResult.Success(decoded) },
            // `runCatching` also catches cancellation; `toAppError` rethrows it.
            onFailure = { failure -> AppResult.Failure(failure.toAppError()) },
        )

    private fun AgentContext.toRequest(): SuggestRequestDto =
        SuggestRequestDto(
            schemaVersion = AGENT_SCHEMA_VERSION,
            // The client's, so a retry is recognisable server-side as the same question.
            requestId = ids.newId(),
            capability = AgentCapability.SUGGEST_FROM_PANTRY.name,
            languageTags = languageTags,
            servings = servings,
            options =
                SuggestOptionsDto(
                    maxResults = options.maxResults,
                    maxMinutes = options.maxMinutes,
                    useOnlyPantry = options.useOnlyPantry,
                ),
            constraints = constraints.map { it.toDto() },
            preferences = preferences.map { it.toDto() },
            avoidedIngredients = avoidedIngredients.map { it.value },
            pantry = pantry.map { it.toDto() },
        )
}

private fun DietaryConstraint.toDto(): ConstraintDto =
    ConstraintDto(term.taxonomy.value, term.term.value, strength.name)

private fun TermRef.toDto(): TermRefDto = TermRefDto(taxonomy.value, term.value)

private fun PantryEntry.toDto(): PantryEntryDto =
    PantryEntryDto(
        ingredientId = ingredient.value,
        amount = quantity.amount,
        unitTaxonomy = quantity.unit?.taxonomy?.value,
        unitTerm = quantity.unit?.term?.value,
        expiringSoon = expiring,
    )
