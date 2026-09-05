package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.time.Instant

/**
 * What the add/edit sheet collected. It exists so the sheet reports its result through one
 * callback instead of a six-parameter lambda nobody can read at the call site.
 *
 * Exactly one of [ingredient] and [freeText] is set: the sheet enforces this before it ever
 * builds a draft, so nothing downstream has to check again.
 */
data class PantryItemDraft(
    val ingredient: IngredientId?,
    val freeText: String?,
    val amount: Double,
    val unit: TermRef?,
    val location: TermRef?,
    val expiresAt: Instant?,
)
