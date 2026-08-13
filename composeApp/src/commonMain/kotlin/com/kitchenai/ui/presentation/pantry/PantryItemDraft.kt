package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TermRef
import kotlin.time.Instant

/**
 * What the add/edit sheet collected. It exists so the sheet reports its result through one
 * callback instead of a five-parameter lambda nobody can read at the call site.
 */
data class PantryItemDraft(
    val ingredient: IngredientId,
    val amount: Double,
    val unit: TermRef?,
    val location: TermRef?,
    val expiresAt: Instant?,
)
