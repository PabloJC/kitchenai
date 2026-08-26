package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.nameOf
import com.kitchenai.ui.presentation.common.wordFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PantryUiMapperTest {
    private val unitRef = TermRef(TaxonomyId.of("units").value(), TermId.of("gram").value())
    private val locationRef = TermRef(TaxonomyId.of("locations").value(), TermId.of("fridge").value())
    private val ingredientId = IngredientId.of("rice").value()
    private val now = Instant.fromEpochSeconds(1_000)

    @Test
    fun `a holding resolves its ingredient and unit names`() {
        val resolver =
            LabelResolver(
                terms = listOf(term(unitRef, "g")),
                ingredients = listOf(ingredient(ingredientId, "Rice")),
                languageTags = listOf("en"),
            )
        val item = pantryItem(quantity = Quantity(200.0, unitRef))

        val ui = item.toUi(resolver, now)

        assertEquals("Rice", ui.name)
        assertEquals("200 g", ui.quantityLabel)
    }

    @Test
    fun `a miss in the catalogue falls back to the identifier`() {
        val item = pantryItem(quantity = Quantity(200.0, null))

        assertEquals(ingredientId.value, item.toUi(LabelResolver(), now).name)
    }

    @Test
    fun `wordFor falls back to the term id on a miss`() {
        assertEquals("gram", LabelResolver().wordFor(unitRef))
    }

    @Test
    fun `nameOf falls back to the ingredient id on a miss`() {
        assertEquals("rice", LabelResolver().nameOf(ingredient(ingredientId, "Rice")))
    }

    @Test
    fun `an edited draft carries the ingredient the sheet changed to rather than the row's original`() {
        val original = pantryItem(quantity = Quantity(100.0, unitRef))
        val edited = IngredientId.of("flour").value()
        val draft =
            PantryItemDraft(
                ingredient = edited,
                freeText = null,
                amount = 300.0,
                unit = unitRef,
                location = null,
                expiresAt = null,
            )

        val applied = original.toUi(LabelResolver(), now).applied(draft, now)

        assertEquals(edited, applied.ingredient)
        assertEquals(300.0, applied.quantity.amount)
    }

    @Test
    fun `a holding with no catalogue match names itself rather than asking the resolver`() {
        val item = pantryItem(quantity = Quantity(1.0, null), freeText = "the good bread")

        assertEquals("the good bread", item.toUi(LabelResolver(), now).name)
    }

    @Test
    fun `an edited draft can turn a catalogue row into a free-text one`() {
        val original = pantryItem(quantity = Quantity(1.0, unitRef))
        val draft =
            PantryItemDraft(
                ingredient = null,
                freeText = "the good bread",
                amount = 1.0,
                unit = unitRef,
                location = null,
                expiresAt = null,
            )

        val applied = original.toUi(LabelResolver(), now).applied(draft, now)

        assertEquals(null, applied.ingredient)
        assertEquals("the good bread", applied.freeText)
    }

    @Test
    fun `optionsIn keeps only the terms in the taxonomies asked for`() {
        val terms = listOf(term(unitRef, "g"), term(locationRef, "Fridge"))
        val resolver = LabelResolver(terms = terms, languageTags = listOf("en"))

        val options = terms.optionsIn(setOf(unitRef.taxonomy), resolver)

        assertEquals(listOf(unitRef to "g"), options)
    }

    @Test
    fun `unitTaxonomies collects only the taxonomies a default unit points at`() {
        val withUnit = ingredient(ingredientId, "Rice", defaultUnit = unitRef)
        val withoutUnit = ingredient(IngredientId.of("salt").value(), "Salt", defaultUnit = null)

        assertEquals(setOf(unitRef.taxonomy), listOf(withUnit, withoutUnit).unitTaxonomies())
    }

    @Test
    fun `purposeful collects only the taxonomies that declare one`() {
        val declared = Taxonomy(unitRef.taxonomy, emptyMap(), purpose = TaxonomyPurpose.UNITS)
        val undeclared = Taxonomy(locationRef.taxonomy, emptyMap(), purpose = null)

        assertEquals(setOf(unitRef.taxonomy), listOf(declared, undeclared).purposeful())
    }

    @Test
    fun `of keeps only the taxonomies matching the purpose asked for`() {
        val units = Taxonomy(unitRef.taxonomy, emptyMap(), purpose = TaxonomyPurpose.UNITS)
        val locations = Taxonomy(locationRef.taxonomy, emptyMap(), purpose = TaxonomyPurpose.STORAGE_LOCATIONS)

        assertEquals(setOf(unitRef.taxonomy), listOf(units, locations).of(TaxonomyPurpose.UNITS))
    }

    @Test
    fun `termTaxonomies collects a holding's unit and its location`() {
        val item = pantryItem(quantity = Quantity(1.0, unitRef), location = locationRef)

        assertEquals(setOf(unitRef.taxonomy, locationRef.taxonomy), listOf(item).termTaxonomies())
    }

    @Test
    fun `locationTaxonomies collects only a holding's location`() {
        val item = pantryItem(quantity = Quantity(1.0, null), location = locationRef)

        assertEquals(setOf(locationRef.taxonomy), listOf(item).locationTaxonomies())
    }

    private fun ingredient(
        id: IngredientId,
        label: String,
        defaultUnit: TermRef? = null,
    ): Ingredient = Ingredient(id, mapOf("en" to label), defaultUnit, emptyList())

    private fun term(
        ref: TermRef,
        label: String,
    ): Term = Term(ref, mapOf("en" to label), null, 0)

    private fun pantryItem(
        quantity: Quantity,
        location: TermRef? = null,
        freeText: String? = null,
    ): PantryItem =
        PantryItem(
            id = PantryItemId.of("item-1").value(),
            ingredient = if (freeText == null) ingredientId else null,
            freeText = freeText,
            quantity = quantity,
            location = location,
            expiresAt = null,
            updatedAt = now,
        )

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).data
}
