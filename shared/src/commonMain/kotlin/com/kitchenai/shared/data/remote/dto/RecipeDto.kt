package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A `recipes/{recipeId}` catalogue document and a `users/{uid}/savedRecipes/{recipeId}` snapshot:
 * the same shape, because a saved recipe is a copy and never a reference.
 *
 * Every field is optional at this level so a document written by an older client fails in the
 * mapper as a validation error rather than as a decoding exception. [savedAtMillis] is written
 * only under the user, and it is what orders the library.
 */
@Serializable
data class RecipeDto(
    val title: String? = null,
    val summary: String? = null,
    val servings: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<RecipeIngredientDto> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<TermRefDto> = emptyList(),
    val source: RecipeSourceDto? = null,
    val savedAtMillis: Long? = null,
)
