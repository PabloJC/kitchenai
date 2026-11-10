package com.kitchenai.admin.ingredients

import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term

data class IngredientsUiState(
    val ingredients: List<Ingredient> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: IngredientQuery = IngredientQuery(),
    val taxonomies: List<Taxonomy> = emptyList(),
    val terms: Map<TaxonomyId, List<Term>> = emptyMap(),
    val editor: IngredientEditorState? = null,
) {
    val shown: List<Ingredient> get() = ingredients.matching(query)

    /** Where a default unit can come from. */
    val unitTaxonomies: List<Taxonomy> get() = taxonomies.filter { it.purpose == TaxonomyPurpose.UNITS }

    /** What an ingredient can be tagged with: every vocabulary that is not a unit, a place or a dish type. */
    val tagTaxonomies: List<Taxonomy> get() = taxonomies.filter { it.purpose !in NOT_INGREDIENT_TAGS }

    private companion object {
        val NOT_INGREDIENT_TAGS =
            setOf(TaxonomyPurpose.UNITS, TaxonomyPurpose.STORAGE_LOCATIONS, TaxonomyPurpose.RECIPE_CLASSIFICATION)
    }
}
