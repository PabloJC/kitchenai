package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.resolve

/**
 * Turns catalogue identifiers into words over one snapshot of the catalogue.
 *
 * It lives outside every feature because all four screens resolve the same identifiers, and it
 * takes snapshots rather than streams so that whoever owns the streams also collects their
 * `errors()` instead of hiding them behind a resolver.
 *
 * A miss returns null and the caller renders the identifier: ugly and honest beats a
 * placeholder that hides a missing translation.
 */
class LabelResolver(
    terms: List<Term> = emptyList(),
    ingredients: List<Ingredient> = emptyList(),
    taxonomies: List<Taxonomy> = emptyList(),
    private val languageTags: List<String> = emptyList(),
) {
    private val termLabels = terms.associate { term -> term.ref to term.labels }
    private val ingredientLabels = ingredients.associate { ingredient -> ingredient.id to ingredient.labels }
    private val fallbackTags = taxonomies.associate { taxonomy -> taxonomy.id to taxonomy.defaultLanguageTag }

    fun label(ref: TermRef): String? = termLabels[ref]?.resolve(languageTags, fallbackTags[ref.taxonomy])

    fun label(id: IngredientId): String? = ingredientLabels[id]?.resolve(languageTags)
}
