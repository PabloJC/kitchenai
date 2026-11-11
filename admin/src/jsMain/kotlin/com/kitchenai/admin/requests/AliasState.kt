package com.kitchenai.admin.requests

import com.kitchenai.shared.domain.model.Ingredient

/** Pointing a request at an ingredient that already exists, optionally keeping the text as one of its names. */
data class AliasState(
    val request: IngredientRequest,
    val ingredientId: String? = null,
    val addLabel: Boolean = false,
    val language: String,
    val isBusy: Boolean = false,
    val message: String? = null,
)

/** The chosen ingredient, if the catalogue still has it. */
fun AliasState.target(catalogue: List<Ingredient>): Ingredient? = catalogue.firstOrNull { it.id.value == ingredientId }

/** Worth offering only when no name of the ingredient already reads the same. */
fun AliasState.isNewSpelling(target: Ingredient): Boolean =
    target.labels.values.none { label -> comparable(label) == comparable(request.text) }

/** The first thing that keeps Alias disabled, or null. */
fun AliasState.problem(catalogue: List<Ingredient>): String? {
    val target = target(catalogue) ?: return "Pick an ingredient"
    return when {
        !addLabel || !isNewSpelling(target) -> null
        language.isBlank() -> "Pick the language of the new name"
        language in target.labels -> "${target.id.value} already has a name in $language"
        else -> null
    }
}
