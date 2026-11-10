package com.kitchenai.admin.ingredients

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef

fun ingredient(
    id: String,
    vararg labels: Pair<String, String>,
    tags: List<TermRef> = emptyList(),
    archived: Boolean = false,
    imageUrl: String? = null,
): Ingredient =
    Ingredient(
        id = (IngredientId.of(id) as AppResult.Success).data,
        labels = labels.toMap(),
        defaultUnit = null,
        tags = tags,
        imageUrl = imageUrl,
        archived = archived,
    )

fun term(
    taxonomy: String,
    term: String,
): TermRef = TermRef((TaxonomyId.of(taxonomy) as AppResult.Success).data, (TermId.of(term) as AppResult.Success).data)
