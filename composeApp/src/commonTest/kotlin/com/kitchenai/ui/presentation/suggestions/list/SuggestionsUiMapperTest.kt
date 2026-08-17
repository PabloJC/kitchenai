package com.kitchenai.ui.presentation.suggestions.list

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AgentId
import com.kitchenai.shared.domain.model.CoveredIngredient
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.MissingIngredient
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.model.RecipeSource
import com.kitchenai.shared.domain.model.RecipeSuggestion
import com.kitchenai.ui.presentation.common.LabelResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SuggestionsUiMapperTest {
    private val ingredientId = IngredientId.of("rice").value()
    private val recipeId = RecipeId.of("recipe-1").value()

    @Test
    fun `an options draft maps field for field onto the domain shape`() {
        val ui = SuggestionOptionsUi(maxResults = 5, maxMinutes = 30, useOnlyPantry = true)

        val domain = ui.toDomain()

        assertEquals(5, domain.maxResults)
        assertEquals(30, domain.maxMinutes)
        assertEquals(true, domain.useOnlyPantry)
    }

    @Test
    fun `a catalogue-sourced suggestion carries no provenance`() {
        val ui = suggestion(source = RecipeSource.Catalogue).toUi(LabelResolver())

        assertNull(ui.provenance)
    }

    @Test
    fun `an agent-sourced suggestion carries the agent and model that answered`() {
        val source = RecipeSource.Agent(AgentId.of("chef").value(), "gpt", Instant.fromEpochSeconds(1))

        val ui = suggestion(source = source).toUi(LabelResolver())

        assertEquals("chef", ui.provenance?.agentId)
        assertEquals("gpt", ui.provenance?.modelId)
    }

    @Test
    fun `held and short counts exclude optional lines`() {
        val required = line(ingredientId, optional = false)
        val optional = line(ingredientId, optional = true)
        val match =
            PantryMatch(
                recipeId = recipeId,
                covered = listOf(CoveredIngredient(required, emptyList()), CoveredIngredient(optional, emptyList())),
                missing = emptyList(),
                unverifiable = emptyList(),
            )

        val ui = suggestion(match = match).toUi(LabelResolver())

        assertEquals(1, ui.heldCount)
        assertEquals(1, ui.totalCount)
    }

    @Test
    fun `a missing catalogue line resolves its name`() {
        val ingredients = listOf(Ingredient(ingredientId, mapOf("en" to "Rice"), null, emptyList()))
        val resolver = LabelResolver(ingredients = ingredients, languageTags = listOf("en"))
        val match =
            PantryMatch(
                recipeId = recipeId,
                covered = emptyList(),
                missing = listOf(MissingIngredient(line(ingredientId, optional = false), null)),
                unverifiable = emptyList(),
            )

        val ui = suggestion(match = match).toUi(resolver)

        assertEquals(listOf("Rice"), ui.missing)
    }

    @Test
    fun `a free-text ingredient names itself`() {
        val freeText =
            RecipeIngredient(ingredient = null, freeText = "A pinch of salt", quantity = null, optional = true)

        assertEquals("A pinch of salt", freeText.name(LabelResolver()))
    }

    @Test
    fun `neither a catalogue id nor free text resolves to an empty name`() {
        val neither = RecipeIngredient(ingredient = null, freeText = null, quantity = null, optional = true)

        assertEquals("", neither.name(LabelResolver()))
    }

    private fun suggestion(
        source: RecipeSource = RecipeSource.Catalogue,
        match: PantryMatch = PantryMatch(recipeId, emptyList(), emptyList(), emptyList()),
    ): RecipeSuggestion =
        RecipeSuggestion(
            recipe =
                Recipe(
                    id = recipeId,
                    title = "Rice bowl",
                    summary = null,
                    servings = 2,
                    totalMinutes = 20,
                    ingredients = emptyList(),
                    steps = emptyList(),
                    tags = emptyList(),
                    source = source,
                ),
            match = match,
            source = source,
        )

    private fun line(
        ingredient: IngredientId,
        optional: Boolean,
    ): RecipeIngredient = RecipeIngredient(ingredient, null, null, optional)

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
