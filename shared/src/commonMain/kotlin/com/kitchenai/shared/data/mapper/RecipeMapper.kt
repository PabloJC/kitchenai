package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.data.remote.dto.RecipeIngredientDto
import com.kitchenai.shared.data.remote.dto.RecipeSourceDto
import com.kitchenai.shared.data.remote.dto.TermRefDto
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.TermRef
import kotlin.time.Instant

/** [savedAt] is the ordering key of the library; it is not domain state, so it enters here. */
fun Recipe.toDto(savedAt: Instant): RecipeDto =
    RecipeDto(
        title = title,
        summary = summary,
        servings = servings,
        totalMinutes = totalMinutes,
        ingredients = ingredients.map { it.toDto() },
        steps = steps,
        tags = tags.map { TermRefDto(it.taxonomy.value, it.term.value) },
        source = source.toDto(),
        savedAtMillis = savedAt.toEpochMilliseconds(),
    )

/**
 * The document id is the identifier: the payload never repeats it. A field the recipe cannot do
 * without fails the document, because a card with no title or no serving count cannot be cooked.
 */
fun RecipeDto.toDomain(documentId: String): AppResult<Recipe> {
    val name = title ?: return missingField("title")
    val portions = servings ?: return missingField("servings")
    return RecipeId.of(documentId).flatMap { id ->
        contents().flatMap { (lines, terms) ->
            source.toDomain().map { provenance ->
                Recipe(id, name, summary, portions, totalMinutes, lines, steps, terms, provenance)
            }
        }
    }
}

/** The two decoded lists, paired so [toDomain] stays one chain rather than three. */
private fun RecipeDto.contents(): AppResult<Pair<List<RecipeIngredient>, List<TermRef>>> =
    ingredients.mapAll(RecipeIngredientDto::toDomain).flatMap { lines ->
        tags.mapAll { tag -> termRef(tag.taxonomy.orEmpty(), tag.term.orEmpty()) }.map { terms -> lines to terms }
    }

private fun RecipeIngredient.toDto(): RecipeIngredientDto =
    RecipeIngredientDto(
        ingredientId = ingredient?.value,
        freeText = freeText,
        amount = quantity?.amount,
        unitTaxonomy = quantity?.unit?.taxonomy?.value,
        unitTerm = quantity?.unit?.term?.value,
        optional = optional,
    )

private fun RecipeIngredientDto.toDomain(): AppResult<RecipeIngredient> =
    ingredientOrNull().flatMap { ingredient ->
        quantityOrNull().flatMap { quantity ->
            RecipeIngredient.create(ingredient, freeText, quantity, optional)
        }
    }

private fun RecipeIngredientDto.ingredientOrNull(): AppResult<IngredientId?> =
    if (ingredientId == null) AppResult.Success(null) else IngredientId.of(ingredientId)

/** A unit with no amount is corruption, not an absent quantity: dropping it would hide that. */
private fun RecipeIngredientDto.quantityOrNull(): AppResult<Quantity?> =
    when {
        amount == null && unitTaxonomy == null && unitTerm == null -> AppResult.Success(null)
        amount == null -> AppResult.Failure(AppError.Validation("amount", "a unit without an amount"))
        else -> termRefOrNull(unitTaxonomy, unitTerm, "unit").map { unit -> Quantity(amount, unit) }
    }

private fun RecipeSource.toDto(): RecipeSourceDto =
    when (this) {
        RecipeSource.Catalogue -> RecipeSourceDto(type = CATALOGUE)
        is RecipeSource.Agent ->
            RecipeSourceDto(AGENT, agentId.value, modelId, generatedAt.toEpochMilliseconds())
    }

/**
 * Provenance is verified, never assumed: an unknown or missing type fails the document instead of
 * decoding to [RecipeSource.Catalogue], which would show generated content as if it were curated.
 */
private fun RecipeSourceDto?.toDomain(): AppResult<RecipeSource> =
    when (this?.type) {
        CATALOGUE -> AppResult.Success(RecipeSource.Catalogue)
        AGENT -> agentSource()
        else -> AppResult.Failure(AppError.Validation("source.type", "is not a known provenance"))
    }

private fun RecipeSourceDto.agentSource(): AppResult<RecipeSource> {
    val agent = agentId ?: return missingField("source.agentId")
    val model = modelId ?: return missingField("source.modelId")
    val generated = generatedAtMillis ?: return missingField("source.generatedAtMillis")
    return AgentId.of(agent).map { id -> RecipeSource.Agent(id, model, Instant.fromEpochMilliseconds(generated)) }
}

private fun missingField(field: String): AppResult.Failure = AppResult.Failure(AppError.Validation(field, "is missing"))

private const val CATALOGUE = "catalogue"
private const val AGENT = "agent"
