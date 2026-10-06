package com.kitchenai.shared.domain.service

import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.freeTextPantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItemId
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.profile.metricUnitConverter
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
    private val gram = termRef("gram")
    private val kilogram = termRef("kilogram")
    private val millilitre = termRef("millilitre")
    private val litre = termRef("litre")
    private val piece = termRef("piece")
    private val metric = metricUnitConverter("taxonomy-1")

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
    fun `a litre of milk covers a recipe asking for 250 millilitres`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(250.0, millilitre))
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(1.0, litre)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now, metric)

        assertEquals(listOf(pantryItemId("item-1")), match.covered.single().heldBy)
        assertEquals(1f, match.coverage)
    }

    @Test
    fun `500 grams cover 0 point 4 kilograms and exactly 0 point 5 kilograms`() {
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(500.0, gram)))

        listOf(0.4, 0.5).forEach { asked ->
            val line = recipeIngredient("ing-1", quantity = Quantity(asked, kilogram))

            val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now, metric)

            assertEquals(1, match.covered.size)
        }
    }

    @Test
    fun `an amount a conversion leaves a hair short is still covered`() {
        // 4.02 kg is 4019.9999999999995 g, so an exact comparison would call it short of 4020 g.
        val line = recipeIngredient("ing-1", quantity = Quantity(4020.0, gram))
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(4.02, kilogram)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now, metric)

        assertEquals(1, match.covered.size)
    }

    @Test
    fun `holdings in different units of one dimension add up`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(0.5, kilogram))
        val pantry =
            listOf(
                pantryItem("item-1", "ing-1", Quantity(300.0, gram)),
                pantryItem("item-2", "ing-1", Quantity(0.2, kilogram)),
            )

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now, metric)

        assertEquals(listOf("item-1", "item-2").map(::pantryItemId), match.covered.single().heldBy)
    }

    @Test
    fun `a converted shortfall is reported in the unit the recipe asked in`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(1.0, kilogram))
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(500.0, gram)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now, metric)

        assertEquals(Quantity(0.5, kilogram), match.missing.single().shortfall)
    }

    @Test
    fun `a holding of another dimension or a piece stays unverifiable even with a converter`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(200.0, gram))

        listOf(Quantity(1.0, litre), Quantity(1.0, piece), Quantity(1.0)).forEach { held ->
            val pantry = listOf(pantryItem("item-1", "ing-1", held))

            val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now, metric)

            assertEquals(listOf(line), match.unverifiable)
        }
    }

    @Test
    fun `convertible units stay unverifiable when the caller passes no converter`() {
        val line = recipeIngredient("ing-1", quantity = Quantity(0.4, kilogram))
        val pantry = listOf(pantryItem("item-1", "ing-1", Quantity(500.0, gram)))

        val match = PantryMatcher.match(recipe(ingredients = listOf(line)), pantry, now)

        assertEquals(listOf(line), match.unverifiable)
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
