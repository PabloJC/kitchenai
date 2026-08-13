package com.kitchenai.shared.data.mapper

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.data.remote.dto.PantryItemDto
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.TermRef
import kotlin.time.Instant

fun PantryItem.toDto(): PantryItemDto =
    PantryItemDto(
        ingredientId = ingredient.value,
        amount = quantity.amount,
        unitTaxonomy = quantity.unit?.taxonomy?.value,
        unitTerm = quantity.unit?.term?.value,
        locationTaxonomy = location?.taxonomy?.value,
        locationTerm = location?.term?.value,
        expiresAtMillis = expiresAt?.toEpochMilliseconds(),
        updatedAtMillis = updatedAt.toEpochMilliseconds(),
    )

/** The document id is the identifier: a pantry document never repeats it in its payload. */
fun PantryItemDto.toDomain(documentId: String): AppResult<PantryItem> =
    PantryItemId.of(documentId).flatMap { id ->
        IngredientId.of(ingredientId).flatMap { ingredient ->
            references().map { (unit, location) ->
                PantryItem(
                    id = id,
                    ingredient = ingredient,
                    quantity = Quantity(amount, unit),
                    location = location,
                    expiresAt = expiresAtMillis?.let(Instant::fromEpochMilliseconds),
                    updatedAt = Instant.fromEpochMilliseconds(updatedAtMillis),
                )
            }
        }
    }

/** The two optional pointers a holding can carry, paired so the chain above stays flat. */
private fun PantryItemDto.references(): AppResult<Pair<TermRef?, TermRef?>> =
    termRefOrNull(unitTaxonomy, unitTerm, "unit").flatMap { unit ->
        termRefOrNull(locationTaxonomy, locationTerm, "location").map { location -> unit to location }
    }
