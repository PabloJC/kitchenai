package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomies
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomy
import com.kitchenai.shared.domain.usecase.shopping.ObserveShoppingItems

/**
 * What the shopping screen watches: the lines of the list it is showing, and the ingredient
 * catalogue behind the add field. Grouped so the ViewModel takes one collaborator per role
 * rather than one parameter per use case; it holds no logic and decides nothing.
 */
class ShoppingReads(
    val items: ObserveShoppingItems,
    val ingredients: ObserveIngredients,
    /** Units are terms, not ingredients: without these a quantity renders as a bare identifier. */
    val taxonomies: ObserveTaxonomies,
    val taxonomy: ObserveTaxonomy,
)
