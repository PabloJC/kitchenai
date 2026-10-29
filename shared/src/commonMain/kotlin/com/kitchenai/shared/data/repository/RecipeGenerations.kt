package com.kitchenai.shared.data.repository

import com.kitchenai.shared.data.local.RecipeEntity
import com.kitchenai.shared.data.local.toRecipe
import com.kitchenai.shared.domain.model.Recipe

/**
 * Rows grouped by the stamp they were stored with, newest first, each in table order. A row that
 * no longer decodes is dropped, and a generation left with nothing is dropped with it.
 */
internal fun List<RecipeEntity>.toGenerations(): List<List<Recipe>> =
    groupBy { it.savedAtMillis }
        .entries
        .sortedByDescending { it.key }
        .map { (_, generation) -> generation.map { it.toRecipe() }.decodedOrDropped() }
        .filter { it.isNotEmpty() }
