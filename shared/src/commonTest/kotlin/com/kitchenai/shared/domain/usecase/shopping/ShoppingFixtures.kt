package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ShoppingItem
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingListId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import kotlin.time.Instant

/** A valid unchecked line, named after its own id so that assertions read by id. */
fun shoppingItem(
    id: String,
    ingredient: String? = id,
    freeText: String? = null,
    quantity: Quantity? = null,
    seconds: Long = 1_000,
): ShoppingItem =
    (
        ShoppingItem.create(
            id = itemId(id),
            updatedAt = instant(seconds),
            ingredient = ingredient?.let(::ingredientId),
            freeText = freeText,
            quantity = quantity,
        ) as AppResult.Success
    ).data

fun userId(raw: String = "user"): UserId = (UserId.of(raw) as AppResult.Success).data

fun listId(raw: String = "list"): ShoppingListId = (ShoppingListId.of(raw) as AppResult.Success).data

fun itemId(raw: String): ShoppingItemId = (ShoppingItemId.of(raw) as AppResult.Success).data

fun ingredientId(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

/** Units are opaque taxonomy terms; nothing in the domain or in a fixture may name one. */
fun termRef(
    taxonomy: String,
    term: String,
): TermRef =
    TermRef(
        (TaxonomyId.of(taxonomy) as AppResult.Success).data,
        (TermId.of(term) as AppResult.Success).data,
    )

fun instant(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

fun fixedTime(seconds: Long): TimeProvider = TimeProvider { instant(seconds) }

fun sequentialIds(prefix: String = "generated"): IdGenerator {
    var next = 0
    return IdGenerator { "$prefix-${++next}" }
}
