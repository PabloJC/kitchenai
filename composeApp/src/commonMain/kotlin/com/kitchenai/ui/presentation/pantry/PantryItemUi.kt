package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.Freshness
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.time.Instant

/**
 * One row of the pantry, already resolved: [name], [quantityLabel] and [locationLabel] are what
 * the list draws, and a label the catalogue does not carry falls back to its own identifier.
 *
 * The identifiers travel alongside the words because the editor sends them straight back to the
 * use cases; the words never do. Exactly one of [ingredient] and [freeText] is set — a holding
 * the catalogue has never heard of, shown in italics, the way the shopping row marks its own.
 */
data class PantryItemUi(
    val id: PantryItemId,
    val ingredient: IngredientId?,
    val freeText: String?,
    val name: String,
    val quantityLabel: String,
    val amount: Double,
    val unit: TermRef?,
    val location: TermRef?,
    val locationLabel: String?,
    val expiresAt: Instant?,
    val freshness: Freshness,
)
