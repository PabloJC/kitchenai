package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.IngredientDto
import com.kitchenai.shared.domain.model.Ingredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IngredientMapperTest {
    @Test
    fun `maps a catalogue document with its labels and its tags`() {
        val dto =
            IngredientDto(
                labels = mapOf("en" to "label-1", "es" to "label-2"),
                defaultUnitTaxonomy = "taxonomy-1",
                defaultUnitTerm = "term-1",
                tags = mapOf("taxonomy-1" to listOf("term-2", "term-3")),
            )

        val mapped = dto.toDomain("ingredient-1")

        val expected =
            Ingredient(
                id = ingredientId("ingredient-1"),
                labels = dto.labels,
                defaultUnit = termRef("term-1"),
                tags = listOf(termRef("term-2"), termRef("term-3")),
            )
        assertEquals(AppResult.Success(expected), mapped)
    }

    @Test
    fun `maps a document that declares no default unit and no tags`() {
        val mapped = IngredientDto(labels = mapOf("en" to "label-1")).toDomain("ingredient-1")

        val expected = Ingredient(ingredientId("ingredient-1"), mapOf("en" to "label-1"), null, emptyList())
        assertEquals(AppResult.Success(expected), mapped)
    }

    @Test
    fun `rejects a default unit that names a taxonomy without its term`() {
        val mapped = IngredientDto(defaultUnitTaxonomy = "taxonomy-1").toDomain("ingredient-1")

        assertEquals(AppResult.Failure(AppError.Validation("defaultUnit", "incomplete term reference")), mapped)
    }

    @Test
    fun `rejects a tag whose term is blank`() {
        val dto = IngredientDto(tags = mapOf("taxonomy-1" to listOf("term-1", " ")))

        val mapped = dto.toDomain("ingredient-1")

        assertTrue(mapped is AppResult.Failure)
        assertEquals(AppError.Validation("TermId", "must not be blank"), mapped.error)
    }

    @Test
    fun `rejects a document whose id is blank`() {
        val mapped = IngredientDto().toDomain("")

        assertTrue(mapped is AppResult.Failure)
        assertEquals(AppError.Validation("IngredientId", "must not be blank"), mapped.error)
    }
}
