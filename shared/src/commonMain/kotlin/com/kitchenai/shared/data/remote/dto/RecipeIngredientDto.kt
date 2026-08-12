package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * One line of a recipe, flat like the pantry and the shopping list: a nested quantity buys
 * nothing and costs a harder index definition.
 *
 * Exactly one of [ingredientId] and [freeText] is set; the domain factory rejects the rest.
 */
@Serializable
data class RecipeIngredientDto(
    val ingredientId: String? = null,
    val freeText: String? = null,
    val amount: Double? = null,
    val unitTaxonomy: String? = null,
    val unitTerm: String? = null,
    val optional: Boolean = false,
)
