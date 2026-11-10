package com.kitchenai.admin.ingredients

import com.kitchenai.shared.data.remote.dto.IngredientDto
import com.kitchenai.shared.domain.model.Ingredient

/** The whole document: `writeCatalogue` replaces it, so a field left out here is a field erased. */
fun IngredientDraft.toDto(): IngredientDto =
    IngredientDto(
        labels = filledLabels().associate { row -> row.language to row.text },
        defaultUnitTaxonomy = defaultUnit?.taxonomy?.value,
        defaultUnitTerm = defaultUnit?.term?.value,
        tags =
            tags
                .groupBy { ref -> ref.taxonomy.value }
                .mapValues { (_, refs) -> refs.map { ref -> ref.term.value }.sorted() },
        purchasedWhole = purchasedWhole,
        imageUrl = imageUrl,
        archived = archived,
    )

/** An existing ingredient opened for editing; every seed language gets a row, filled or not. */
fun Ingredient.toDraft(): IngredientDraft {
    val named = labels.map { (language, text) -> LabelRow(language, text) }
    val missing = NEW_LABEL_LANGUAGES.filterNot { language -> language in labels }.map { LabelRow(it, "") }
    return IngredientDraft(
        id = id.value,
        isNew = false,
        labels = named + missing,
        defaultUnit = defaultUnit,
        tags = tags.toSet(),
        purchasedWhole = purchasedWhole,
        imageUrl = imageUrl,
        archived = archived,
    )
}
