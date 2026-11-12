package com.kitchenai.admin.requests

import kotlinx.serialization.Serializable

/** What `migrateFreeText` returns: counts only, never which kitchens. */
@Serializable
data class ConversionCounts(
    val pantryItems: Int,
    val shoppingItems: Int,
    val dryRun: Boolean,
) {
    val total: Int get() = pantryItems + shoppingItems
}
