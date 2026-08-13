package com.kitchenai.shared.data.remote.agent

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.agent.dto.SuggestResponseDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestedIngredientDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestedRecipeDto
import com.kitchenai.shared.data.remote.agent.dto.SuggestedTagDto
import com.kitchenai.shared.domain.agent.AgentSuggestions
import com.kitchenai.shared.domain.model.RecipeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Model output is untrusted input on its way to a screen and a Firestore document. Everything
 * here is about what the validator refuses, because what it accepts is the easy half.
 */
class SuggestionValidatorTest {
    @Test
    fun `maps a well formed response and stamps it with the response's own provenance`() {
        val validated = validate(response(suggestions = listOf(suggestion())))

        val suggestions = validated.orFail()
        assertEquals(1, suggestions.suggestions.size)
        val recipe = suggestions.suggestions.single()
        assertEquals("Title", recipe.title)
        assertEquals(2, recipe.servings)
        val source = recipe.source as RecipeSource.Agent
        assertEquals("agent-1", source.agentId.value)
        assertEquals("model-1", source.modelId)
        assertEquals(NOW, source.generatedAt)
    }

    @Test
    fun `refuses a response announcing a schema this client does not speak`() {
        val validated = validate(response(suggestions = listOf(suggestion())).copy(schemaVersion = 2))

        val error = (validated as AppResult.Failure).error as AppError.Validation
        assertEquals("schemaVersion", error.field)
    }

    @Test
    fun `refuses a response that will not say which model wrote it`() {
        val validated = validate(response(suggestions = listOf(suggestion())).copy(modelId = "  "))

        val error = (validated as AppResult.Failure).error as AppError.Validation
        assertEquals("modelId", error.field)
    }

    @Test
    fun `truncates a response that returned more than was asked for`() {
        val validated = validate(response(suggestions = List(9) { suggestion() }), maxResults = 3)

        assertEquals(3, validated.orFail().suggestions.size)
    }

    @Test
    fun `drops a malformed suggestion and keeps its siblings`() {
        val broken =
            listOf(
                suggestion(title = "Kept"),
                suggestion(steps = emptyList()),
                suggestion(ingredients = emptyList()),
                suggestion(servings = 0),
                suggestion(title = "   "),
                suggestion(title = "Also kept"),
            )

        val titles = validate(response(suggestions = broken)).orFail().suggestions.map { it.title }

        assertEquals(listOf("Kept", "Also kept"), titles)
    }

    @Test
    fun `strips control characters and line breaks from everything it shows`() {
        // Written as escapes rather than as raw bytes: a source file carrying a NUL is a file
        // no reviewer can read, and the point is to name which characters are being refused.
        val nasty =
            suggestion(
                title = "Ti\u0000tle\n",
                summary = "Sum\u0007mary",
                steps = listOf("St\u001Bep"),
            )

        val recipe = validate(response(suggestions = listOf(nasty))).orFail().suggestions.single()

        assertEquals("Title", recipe.title)
        assertEquals("Summary", recipe.summary)
        assertEquals(listOf("Step"), recipe.steps)
    }

    @Test
    fun `caps a title and a step that would otherwise fill the screen`() {
        val long = suggestion(title = "t".repeat(400), steps = listOf("s".repeat(4_000)))

        val recipe = validate(response(suggestions = listOf(long))).orFail().suggestions.single()

        assertEquals(120, recipe.title.length)
        assertEquals(1_000, recipe.steps.single().length)
    }

    @Test
    fun `keeps a free-text line and a catalogue line and drops a broken pointer`() {
        val lines =
            listOf(
                SuggestedIngredientDto(ingredientId = "ingredient-1", amount = 2.0),
                SuggestedIngredientDto(freeText = "a pinch of something"),
                SuggestedIngredientDto(ingredientId = "   ", amount = 1.0),
            )

        val validated = validate(response(suggestions = listOf(suggestion(ingredients = lines))))
        val recipe = validated.orFail().suggestions.single()

        assertEquals(2, recipe.ingredients.size)
        assertEquals("ingredient-1", recipe.ingredients.first().ingredient?.value)
        assertEquals("a pinch of something", recipe.ingredients.last().freeText)
    }

    @Test
    fun `drops a quantity that is not a quantity and a duration that is not a duration`() {
        val nonsense =
            suggestion(
                ingredients = listOf(SuggestedIngredientDto(freeText = "salt", amount = -3.0)),
                totalMinutes = 0,
            )

        val recipe = validate(response(suggestions = listOf(nonsense))).orFail().suggestions.single()

        assertNull(recipe.ingredients.single().quantity)
        assertNull(recipe.totalMinutes)
    }

    @Test
    fun `drops a half-specified tag rather than inventing the missing half`() {
        val tagged =
            suggestion(
                tags = listOf(SuggestedTagDto("taxonomy-1", "term-1"), SuggestedTagDto(taxonomy = "taxonomy-1")),
            )

        val recipe = validate(response(suggestions = listOf(tagged))).orFail().suggestions.single()

        assertEquals(1, recipe.tags.size)
    }

    @Test
    fun `gives every suggestion its own identity`() {
        val validated = validate(response(suggestions = List(3) { suggestion() }))

        val ids = validated.orFail().suggestions.map { it.id.value }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `answers an empty list of suggestions rather than failing`() {
        val validated = validate(response(suggestions = emptyList()))

        assertTrue(validated.orFail().suggestions.isEmpty())
    }

    private fun validate(
        response: SuggestResponseDto,
        maxResults: Int = 5,
    ): AppResult<AgentSuggestions> {
        var counter = 0
        val validator = SuggestionValidator(newRecipeId = { "recipe-${counter++}" }, now = { NOW })
        return validator.validate(response, maxResults)
    }

    private fun response(suggestions: List<SuggestedRecipeDto>): SuggestResponseDto =
        SuggestResponseDto(
            schemaVersion = AGENT_SCHEMA_VERSION,
            agentId = "agent-1",
            modelId = "model-1",
            suggestions = suggestions,
        )

    private fun suggestion(
        title: String? = "Title",
        summary: String? = "Summary",
        servings: Int? = 2,
        totalMinutes: Int? = 30,
        ingredients: List<SuggestedIngredientDto> = listOf(SuggestedIngredientDto(ingredientId = "ingredient-1")),
        steps: List<String> = listOf("Step"),
        tags: List<SuggestedTagDto> = emptyList(),
    ): SuggestedRecipeDto = SuggestedRecipeDto(title, summary, servings, totalMinutes, ingredients, steps, tags)

    private fun <T> AppResult<T>.orFail(): T =
        when (this) {
            is AppResult.Success -> data
            is AppResult.Failure -> throw AssertionError("expected success but got $error")
        }

    private companion object {
        val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }
}
