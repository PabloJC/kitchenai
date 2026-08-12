package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.PantryItemDto
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class PantryItemMapperTest {
    @Test
    fun `a holding round trips through the document shape`() {
        val item =
            PantryItem(
                id = pantryItemId("item-1"),
                ingredient = ingredientId("ingredient-1"),
                quantity = Quantity(2.5, termRef("term-1")),
                location = termRef("term-2"),
                expiresAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
                updatedAt = Instant.fromEpochMilliseconds(1_699_000_000_000),
            )

        val restored = item.toDto().toDomain(item.id.value)

        assertEquals(AppResult.Success(item), restored)
    }

    @Test
    fun `carries an absent expiry and an absent location through as null`() {
        val item =
            PantryItem(
                id = pantryItemId("item-1"),
                ingredient = ingredientId("ingredient-1"),
                quantity = Quantity(1.0),
                location = null,
                expiresAt = null,
                updatedAt = Instant.fromEpochMilliseconds(0),
            )

        val dto = item.toDto()

        assertEquals(null, dto.expiresAtMillis)
        assertEquals(null, dto.locationTaxonomy)
        assertEquals(AppResult.Success(item), dto.toDomain(item.id.value))
    }

    @Test
    fun `rejects a unit that names a taxonomy without its term`() {
        val dto = document(unitTaxonomy = "taxonomy-1")

        val mapped = dto.toDomain("item-1")

        assertEquals(AppResult.Failure(AppError.Validation("unit", "incomplete term reference")), mapped)
    }

    @Test
    fun `rejects a location that names a term without its taxonomy`() {
        val dto = document(locationTerm = "term-1")

        val mapped = dto.toDomain("item-1")

        assertEquals(AppResult.Failure(AppError.Validation("location", "incomplete term reference")), mapped)
    }

    @Test
    fun `rejects a document whose id is blank`() {
        val mapped = document().toDomain(" ")

        assertTrue(mapped is AppResult.Failure)
        assertEquals(AppError.Validation("PantryItemId", "must not be blank"), mapped.error)
    }

    @Test
    fun `rejects a document whose ingredient is blank`() {
        val mapped = document(ingredientId = "").toDomain("item-1")

        assertTrue(mapped is AppResult.Failure)
        assertEquals(AppError.Validation("IngredientId", "must not be blank"), mapped.error)
    }
}

// Fixtures. Every identifier here is opaque on purpose: naming a unit, a location or an
// ingredient in a fixture is the same mistake as naming it in code.
internal fun document(
    ingredientId: String = "ingredient-1",
    unitTaxonomy: String? = null,
    locationTerm: String? = null,
): PantryItemDto =
    PantryItemDto(
        ingredientId = ingredientId,
        amount = 1.0,
        unitTaxonomy = unitTaxonomy,
        locationTerm = locationTerm,
        updatedAtMillis = 0,
    )

internal fun <T> AppResult<T>.orFail(): T = (this as AppResult.Success).data

internal fun pantryItemId(raw: String): PantryItemId = PantryItemId.of(raw).orFail()

internal fun ingredientId(raw: String): IngredientId = IngredientId.of(raw).orFail()

internal fun termRef(term: String): TermRef = TermRef(TaxonomyId.of("taxonomy-1").orFail(), TermId.of(term).orFail())
