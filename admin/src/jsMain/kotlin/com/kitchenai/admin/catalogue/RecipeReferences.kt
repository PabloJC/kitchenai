package com.kitchenai.admin.catalogue

import com.kitchenai.shared.data.remote.dto.RecipeDto

/** The catalogue recipes whose lines name [ingredientId], by recipe id: what a hard delete would break. */
fun recipesReferencing(
    ingredientId: String,
    recipes: Map<String, RecipeDto>,
): List<String> =
    recipes
        .filterValues { recipe -> recipe.ingredients.any { line -> line.ingredientId == ingredientId } }
        .keys
        .sorted()
