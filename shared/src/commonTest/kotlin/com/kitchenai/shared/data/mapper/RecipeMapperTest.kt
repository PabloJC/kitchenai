package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.RecipeDto
import com.kitchenai.shared.data.remote.dto.RecipeIngredientDto
import com.kitchenai.shared.data.remote.dto.RecipeSourceDto
import com.kitchenai.shared.data.remote.dto.TermRefDto
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.recipe.recipe
import com.kitchenai.shared.domain.usecase.recipe.recipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Every value here is opaque: a title, a cuisine or an ingredient named in a fixture is the same
 * mistake as naming it in code.
 */
class RecipeMapperTest {
    @Test
    fun `a catalogue recipe round trips through the document shape`() {
        val original =
            recipe(ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(2.0, termRef("term-1")))))
                .copy(summary = "summary-1", totalMinutes = 25, steps = listOf("step-1"), tags = listOf(termRef("t-1")))

        val restored = original.toDto(SAVED_AT).toDomain(original.id.value)

        assertEquals(AppResult.Success(original), restored)
    }

    @Test
    fun `a generated recipe keeps its agent its model and its instant`() {
        val agent = (AgentId.of("agent-1") as AppResult.Success).data
        val original =
            recipe(ingredients = listOf(recipeIngredient(freeText = "free-text-1")))
                .copy(source = RecipeSource.Agent(agent, "model-1", SAVED_AT))

        val restored = original.toDto(SAVED_AT).toDomain(original.id.value)

        assertEquals(AppResult.Success(original), restored)
    }

    @Test
    fun `stamps the moment a recipe was saved so the library can be ordered by it`() {
        val document = recipe().toDto(SAVED_AT)

        assertEquals(SAVED_AT.toEpochMilliseconds(), document.savedAtMillis)
    }

    @Test
    fun `rejects a document whose source type is unknown`() {
        val mapped = recipeDocument(source = RecipeSourceDto(type = "type-1")).toDomain("recipe-1")

        assertEquals(AppResult.Failure(UNKNOWN_SOURCE), mapped)
    }

    @Test
    fun `rejects a document carrying no source at all`() {
        val mapped = recipeDocument(source = null).toDomain("recipe-1")

        assertEquals(AppResult.Failure(UNKNOWN_SOURCE), mapped)
    }

    @Test
    fun `rejects a generated document that names no model`() {
        val source = RecipeSourceDto("agent", agentId = "agent-1", generatedAtMillis = 1_000)

        val mapped = recipeDocument(source = source).toDomain("recipe-1")

        assertEquals(AppResult.Failure(AppError.Validation("source.modelId", "is missing")), mapped)
    }

    @Test
    fun `rejects a generated document that names no agent`() {
        val source = RecipeSourceDto("agent", modelId = "model-1", generatedAtMillis = 1_000)

        val mapped = recipeDocument(source = source).toDomain("recipe-1")

        assertEquals(AppResult.Failure(AppError.Validation("source.agentId", "is missing")), mapped)
    }

    @Test
    fun `rejects a document with no title`() {
        val mapped = recipeDocument().copy(title = null).toDomain("recipe-1")

        assertEquals(AppResult.Failure(AppError.Validation("title", "is missing")), mapped)
    }

    @Test
    fun `rejects a document with no serving count`() {
        val mapped = recipeDocument().copy(servings = null).toDomain("recipe-1")

        assertEquals(AppResult.Failure(AppError.Validation("servings", "is missing")), mapped)
    }

    @Test
    fun `rejects a line that names an ingredient and a free text line at once`() {
        val line = RecipeIngredientDto(ingredientId = "ing-1", freeText = "free-text-1")

        val mapped = recipeDocument(ingredients = listOf(line)).toDomain("recipe-1")

        assertEquals(AppResult.Failure(ONE_OF), mapped)
    }

    @Test
    fun `rejects a line carrying a unit without an amount`() {
        val line = RecipeIngredientDto(ingredientId = "ing-1", unitTaxonomy = "taxonomy-1", unitTerm = "term-1")

        val mapped = recipeDocument(ingredients = listOf(line)).toDomain("recipe-1")

        assertEquals(AppResult.Failure(AppError.Validation("amount", "a unit without an amount")), mapped)
    }

    @Test
    fun `rejects a tag that names a taxonomy without its term`() {
        val mapped = recipeDocument(tags = listOf(TermRefDto(taxonomy = "taxonomy-1"))).toDomain("recipe-1")

        assertEquals(AppResult.Failure(AppError.Validation("TermId", "must not be blank")), mapped)
    }

    @Test
    fun `rejects a document whose id is blank`() {
        val mapped = recipeDocument().toDomain(" ")

        assertEquals(AppResult.Failure(AppError.Validation("RecipeId", "must not be blank")), mapped)
    }

    private companion object {
        val SAVED_AT: Instant = Instant.fromEpochMilliseconds(1_000)
        val UNKNOWN_SOURCE = AppError.Validation("source.type", "is not a known provenance")

        // The invariant the domain factory rejects on; the mapper is expected to relay it verbatim.
        val ONE_OF = AppError.Validation("ingredient", "exactly one of ingredient or freeText must be set")
    }
}

/** A recipe as Firestore stores it, valid unless a test breaks one field of it. */
internal fun recipeDocument(
    ingredients: List<RecipeIngredientDto> = listOf(RecipeIngredientDto(ingredientId = "ing-1")),
    tags: List<TermRefDto> = emptyList(),
    source: RecipeSourceDto? = RecipeSourceDto(type = "catalogue"),
    summary: String? = null,
): RecipeDto =
    RecipeDto(
        title = "title-1",
        summary = summary,
        servings = 2,
        ingredients = ingredients,
        steps = listOf("step-1"),
        tags = tags,
        source = source,
        savedAtMillis = 1_000,
    )
