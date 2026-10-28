package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermRef

/**
 * What a line needs beyond itself: the shop section its ingredient sits in, and the title of the
 * recipe it was added for. Either missing leaves the line as it was, never an identifier.
 */
internal class ShoppingLineContext(
    val sectionOf: Map<IngredientId, TermRef> = emptyMap(),
    val recipeTitles: Map<RecipeId, String> = emptyMap(),
) {
    companion object {
        /** The first tag an ingredient carries from a [sections] vocabulary is its place in the shop. */
        fun of(
            catalogue: List<Ingredient>,
            sections: Set<TaxonomyId>,
            recipeTitles: Map<RecipeId, String>,
        ): ShoppingLineContext =
            ShoppingLineContext(
                sectionOf =
                    catalogue
                        .mapNotNull { ingredient ->
                            ingredient.tags.firstOrNull { tag -> tag.taxonomy in sections }?.let { ingredient.id to it }
                        }.toMap(),
                recipeTitles = recipeTitles,
            )
    }
}
