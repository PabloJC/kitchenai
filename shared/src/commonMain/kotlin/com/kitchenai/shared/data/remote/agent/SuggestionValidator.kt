package com.kitchenai.shared.data.remote.agent

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.getOrElse
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.agent.dto.SuggestResponseDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestedIngredientDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestedRecipeDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestedTagDto
import com.kitchenai.shared.domain.agent.AgentSuggestions
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.time.Instant

/** The only schema this client speaks. A response announcing another one is refused, not guessed at. */
internal const val AGENT_SCHEMA_VERSION = 1

private const val MAX_TITLE_LENGTH = 120
private const val MAX_SUMMARY_LENGTH = 400
private const val MAX_STEP_LENGTH = 1_000
private const val MAX_STEPS = 40
private const val MAX_INGREDIENTS = 60

/**
 * A hard ceiling on what is even looked at, independent of what the caller asked for. It bounds
 * the work a response can cost us when the answer arrives far larger than anyone requested.
 */
private const val MAX_SUGGESTIONS_CONSIDERED = 50

/**
 * Turns a response into recipes, dropping everything that does not survive inspection.
 *
 * Model output is untrusted input that reaches a screen and a Firestore document, so this is
 * where it stops being a payload and becomes a [Recipe]. Two rules hold throughout:
 *
 * - **A bad suggestion costs itself, never its siblings.** One unusable dish must not empty a
 *   list the user is waiting on.
 * - **No field is ever read as an instruction.** Everything here is length, shape and character
 *   class. If a later issue makes the client act on a field, it passes through here first.
 */
internal class SuggestionValidator(
    private val newRecipeId: () -> String,
    private val now: () -> Instant,
) {
    fun validate(
        response: SuggestResponseDto,
        maxResults: Int,
    ): AppResult<AgentSuggestions> {
        if (response.schemaVersion != AGENT_SCHEMA_VERSION) {
            return AppResult.Failure(AppError.Validation("schemaVersion", "unsupported response schema"))
        }
        return AgentId.of(response.agentId).flatMap { agentId ->
            response.modelId.asModelId().map { modelId ->
                // Provenance is built from what the response says it is, never from the caller's
                // expectation: a suggestion has to be able to say which model wrote it.
                val source = RecipeSource.Agent(agentId, modelId, now())
                AgentSuggestions(
                    agentId = agentId,
                    modelId = modelId,
                    // Dropped first, capped second: `maxResults` bounds what the user is shown,
                    // so a suggestion that does not survive inspection must not spend that budget.
                    suggestions =
                        response.suggestions
                            .take(MAX_SUGGESTIONS_CONSIDERED)
                            .mapNotNull { it.toRecipe(source) }
                            .take(maxResults),
                )
            }
        }
    }

    private fun SuggestedRecipeDto.toRecipe(source: RecipeSource.Agent): Recipe? {
        val cleanTitle = title.clean(MAX_TITLE_LENGTH)
        val cleanServings = servings?.takeIf { it >= 1 }
        // The id is the client's: the response has no place to put one, and a dish that has
        // never been saved has no identity the server could know.
        val id = RecipeId.of(newRecipeId()).getOrElse { null }
        if (cleanTitle == null || cleanServings == null || id == null) return null

        val lines = ingredients.take(MAX_INGREDIENTS).mapNotNull { it.toLine() }
        val cleanSteps = steps.take(MAX_STEPS).mapNotNull { it.clean(MAX_STEP_LENGTH) }
        if (lines.isEmpty() || cleanSteps.isEmpty()) return null

        return Recipe(
            id = id,
            title = cleanTitle,
            summary = summary.clean(MAX_SUMMARY_LENGTH),
            servings = cleanServings,
            // Zero or negative is not a duration; absent says so honestly.
            totalMinutes = totalMinutes?.takeIf { it > 0 },
            ingredients = lines,
            steps = cleanSteps,
            tags = tags.mapNotNull { it.toRef() },
            source = source,
        )
    }

    private fun SuggestedIngredientDto.toLine(): RecipeIngredient? {
        val pointer = ingredientId?.let { raw -> IngredientId.of(raw).getOrElse { null } }
        // A pointer that will not parse drops its line rather than falling back to free text:
        // that would turn a broken catalogue reference into something the pantry cannot check.
        if (ingredientId != null && pointer == null) return null
        return RecipeIngredient
            .create(
                ingredient = pointer,
                freeText = if (pointer == null) freeText.clean(MAX_TITLE_LENGTH) else null,
                quantity = amount?.takeIf { it > 0 }?.let { Quantity(it, termRef(unitTaxonomy, unitTerm)) },
                optional = optional,
            ).getOrElse { null }
    }

    private fun SuggestedTagDto.toRef(): TermRef? = termRef(taxonomy, term)
}

private fun termRef(
    taxonomy: String?,
    term: String?,
): TermRef? {
    val id = taxonomy?.let { raw -> TaxonomyId.of(raw).getOrElse { null } } ?: return null
    val name = term?.let { raw -> TermId.of(raw).getOrElse { null } } ?: return null
    return TermRef(id, name)
}

private fun String.asModelId(): AppResult<String> =
    if (isBlank()) {
        AppResult.Failure(AppError.Validation("modelId", "is missing"))
    } else {
        AppResult.Success(take(MAX_TITLE_LENGTH))
    }

/**
 * Strips control characters and caps the length. Line breaks go with them: a step is one
 * paragraph, and a model must not get to lay out the screen through its own output.
 */
private fun String?.clean(max: Int): String? =
    this
        ?.filter { character -> !character.isISOControl() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(max)
