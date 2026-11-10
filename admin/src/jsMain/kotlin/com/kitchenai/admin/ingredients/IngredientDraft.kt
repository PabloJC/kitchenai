package com.kitchenai.admin.ingredients

import com.kitchenai.shared.domain.model.TermRef

/** The editor's working copy, in plain strings so a half-typed field can exist. */
data class IngredientDraft(
    val id: String = "",
    /** The id is fixed once the document exists: pantries point at it. */
    val isNew: Boolean = true,
    val labels: List<LabelRow> = NEW_LABEL_LANGUAGES.map { language -> LabelRow(language, "") },
    val defaultUnit: TermRef? = null,
    val tags: Set<TermRef> = emptySet(),
    val purchasedWhole: Boolean = false,
    val imageUrl: String? = null,
    val archived: Boolean = false,
)

/** The languages the seed catalogue names everything in; more can be added row by row. */
val NEW_LABEL_LANGUAGES: List<String> = listOf("en", "es")
