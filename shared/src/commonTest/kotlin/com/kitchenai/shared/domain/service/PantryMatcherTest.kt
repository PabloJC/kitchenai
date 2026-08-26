package com.kitchenai.shared.domain.service

import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.freeTextPantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItemId
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.recipe.recipe
import com.kitchenai.shared.domain.usecase.recipe.recipeId
import com.kitchenai.shared.domain.usecase.recipe.recipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class PantryMatcherTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val unitA = termRef("term-1")
    private val unitB = termRef("term-2")

    @Test
    fun `a line with no catalogue id is unverifiable and never guessed at`() {
        val line = recipeIngredient(freeText = "free-text-1")

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), emptyList(), now)

        assertEquals(listOf(line), match.unverifiable)
        assertTrue(match.covered.isEmpty())
        assertTrue(match.missing.isEmpty())
    }

    @Test
    fun `a free-text holding covers no recipe line even one asking for the same words`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(1.0, unitA))
        val pantry = listOf(freeTextPantryItem("item-1", "ing-1", Quantity(1.0, unitA)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now)

        assertTrue(match.covered.isEmpty())
        assertEquals(Quantity(1.0, unitA), match.missing.single().shortfall)
    }

    @Test
    fun `an amount held in another unit is unverifiable rather than missing`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(200.0, unitA))
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(200.0, unitB)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now)

        assertEquals(listOf(line), match.unverifiable)
        assertTrue(match.missing.isEmpty())
    }

    @Test
    fun `a line with no amount is covered by any holding at all`() {
        val line = recipeIngredient("ing-1")
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, unitA)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now)

        assertEquals(listOf(pantryItemId("item-1")), match.covered.single().heldBy)
    }

    @Test
    fun `a line with no amount and nothing held is missing without a shortfall`() {
        val line = recipeIngredient("ing-1")

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), emptyList(), now)

        assertEquals(null, match.missing.single().shortfall)
    }

    @Test
    fun `an expired holding covers nothing`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(1.0, unitA))
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(5.0, unitA), expiresAt = now))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now)

        assertTrue(match.covered.isEmpty())
        assertEquals(Quantity(1.0, unitA), match.missing.single().shortfall)
    }

    @Test
    fun `holdings of the same ingredient add up`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(300.0, unitA))
        val pantry =
            listOf(
                pantryItem("item-1", "ing-1", Quantity(100.0, unitA)),
                pantryItem("item-2", "ing-1", Quantity(200.0, unitA)),
            )

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now)

        assertEquals(listOf("item-1", "item-2").map(::pantryItemId), match.covered.single().heldBy)
    }

    @Test
    fun `a holding smaller than the recipe asks for is missing the difference`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(500.0, unitA))
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(200.0, unitA)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now)

        assertEquals(Quantity(300.0, unitA), match.missing.single().shortfall)
    }

    @Test
    fun `an optional line is reported but left out of the coverage`() {
        val required = recipeIngredient("ing-1", quantity = Quantity(1.0, unitA))
        val garnish = recipeIngredient("ing-2", quantity = Quantity(1.0, unitA), optional = true)
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, unitA)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(required, garnish)), pantry, now)

        assertEquals(listOf(garnish), match.missing.map { it.ingredient })
        assertEquals(1f, match.coverage)
    }

    @Test
    fun `unverifiable lines are left out of the coverage`() {
        val known = recipeIngredient("ing-1", quantity = Quantity(1.0, unitA))
        val text = recipeIngredient(freeText = "free-text-1")
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, unitA)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(known, text)), pantry, now)

        assertEquals(1, match.unverifiable.size)
        assertEquals(1f, match.coverage)
    }

    @Test
    fun `coverage is the share of the required lines the pantry holds`() {
        val first = recipeIngredient("ing-1", quantity = Quantity(1.0, unitA))
        val second = recipeIngredient("ing-2", quantity = Quantity(1.0, unitA))
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, unitA)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(first, second)), pantry, now)

        assertEquals(0.5f, match.coverage)
    }

    @Test
    fun `an empty pantry leaves every line missing`() {
        val lines =
            listOf(
                recipeIngredient("ing-1", quantity = Quantity(1.0, unitA)),
                recipeIngredient("ing-2"),
            )

        val match = PantryMatcher.match(recipe(ingredients = lines), emptyList(), now)

        assertEquals(2, match.missing.size)
        assertEquals(0f, match.coverage)
    }

    @Test
    fun `a recipe with no ingredients matches to nothing at all`() {
        val match = PantryMatcher.match(recipe(), emptyList(), now)

        assertEquals(recipeId("recipe-1"), match.recipeId)
        assertTrue(match.covered.isEmpty())
        assertTrue(match.missing.isEmpty())
        assertTrue(match.unverifiable.isEmpty())
        assertEquals(0f, match.coverage)
    }
}
