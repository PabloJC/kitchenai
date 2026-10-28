package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.LabelResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ShoppingUiMapperTest {
    private val unitRef = TermRef(TaxonomyId.of("units").value(), TermId.of("gram").value())
    private val ingredientId = IngredientId.of("rice").value()
    private val now = Instant.fromEpochSeconds(1_000)

    @Test
    fun `a catalogue line resolves its ingredient name and quantity`() {
        val resolver =
            LabelResolver(
                terms = listOf(Term(unitRef, mapOf("en" to "g"), null, 0)),
                ingredients = listOf(Ingredient(ingredientId, mapOf("en" to "Rice"), null, emptyList())),
                languageTags = listOf("en"),
            )
        val item = item(ingredient = ingredientId, quantity = Quantity(200.0, unitRef))

        val ui = item.toUi(resolver)

        assertEquals("Rice", ui.label)
        assertEquals("200 g", ui.quantity)
        assertEquals(true, ui.fromCatalogue)
    }

    @Test
    fun `a countable unit agrees in number with the amount`() {
        val countable = Term(unitRef, mapOf("es" to "unidad"), null, 0, pluralLabels = mapOf("es" to "unidades"))
        val resolver = LabelResolver(terms = listOf(countable), languageTags = listOf("es"))

        assertEquals("1 unidad", item(quantity = Quantity(1.0, unitRef)).toUi(resolver).quantity)
        assertEquals("2 unidades", item(quantity = Quantity(2.0, unitRef)).toUi(resolver).quantity)
    }

    @Test
    fun `a spanish reader sees a decimal comma`() {
        val resolver = LabelResolver(languageTags = listOf("es-ES"))

        assertEquals("1,5", item(quantity = Quantity(1.5, null)).toUi(resolver).quantity)
    }

    @Test
    fun `a free-text line has no catalogue label`() {
        val item = item(freeText = "Toothpaste")

        val ui = item.toUi(LabelResolver())

        assertEquals("Toothpaste", ui.label)
        assertEquals(false, ui.fromCatalogue)
    }

    @Test
    fun `a catalogue miss falls back to the identifier`() {
        val item = item(ingredient = ingredientId)

        assertEquals(ingredientId.value, item.label(LabelResolver()))
    }

    @Test
    fun `a whole amount renders without a decimal point`() {
        val item = item(ingredient = ingredientId, quantity = Quantity(2.0, null))

        assertEquals("2", item.toUi(LabelResolver()).quantity)
    }

    @Test
    fun `lines group by shop section in the catalogue's order with the unclaimed ones last`() {
        val fruit = section("fruit")
        val dairy = section("dairy")
        val lines =
            listOf(
                ui("milk", dairy),
                ui("bread", null),
                ui("apple", fruit),
                ui("yogurt", dairy),
                ui("stray", section("retired")),
            )

        val groups = lines.inSections(listOf(fruit to "Fruit", dairy to "Dairy"))

        assertEquals(listOf("Fruit", "Dairy", null), groups.map { it.title })
        assertEquals(
            listOf(listOf("apple"), listOf("milk", "yogurt"), listOf("bread", "stray")),
            groups.map { group -> group.lines.map { it.label } },
        )
    }

    @Test
    fun `with no sections declared every line is one untitled group`() {
        val groups = listOf(ui("milk", null), ui("bread", null)).inSections(emptyList())

        assertEquals(listOf(null), groups.map { it.title })
        assertEquals(2, groups.single().lines.size)
    }

    @Test
    fun `a line takes its ingredient's first tag from the shop sections and its recipe's title`() {
        val dairy = section("dairy")
        val diet = TermRef(TaxonomyId.of("diets").value(), TermId.of("vegan").value())
        val milk = Ingredient(ingredientId, mapOf("en" to "Milk"), null, listOf(diet, dairy))
        val dish = RecipeId.of("recipe-1").value()
        val context = ShoppingLineContext.of(listOf(milk), setOf(dairy.taxonomy), mapOf(dish to "Pasta alla Norma"))

        val ui = item(ingredient = ingredientId, sourceRecipe = dish).toUi(LabelResolver(), context)

        assertEquals(dairy, ui.section)
        assertEquals("Pasta alla Norma", ui.forRecipe)
    }

    @Test
    fun `a recipe with no known title shows nothing rather than its identifier`() {
        val ui = item(freeText = "coconut milk", sourceRecipe = RecipeId.of("uuid-1").value()).toUi(LabelResolver())

        assertNull(ui.forRecipe)
        assertNull(ui.section)
    }

    private fun section(term: String) = TermRef(TaxonomyId.of("food-groups").value(), TermId.of(term).value())

    private fun ui(
        label: String,
        section: TermRef?,
    ) = ShoppingItemUi(
        id = ShoppingItemId.of(label).value(),
        label = label,
        quantity = null,
        fromCatalogue = section != null,
        checked = false,
        section = section,
    )

    private fun item(
        ingredient: IngredientId? = null,
        freeText: String? = null,
        quantity: Quantity? = null,
        sourceRecipe: RecipeId? = null,
    ): ShoppingItem =
        ShoppingItem(
            id = ShoppingItemId.of("item-1").value(),
            ingredient = ingredient,
            freeText = freeText,
            quantity = quantity,
            checked = false,
            sourceRecipe = sourceRecipe,
            updatedAt = now,
        )

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
