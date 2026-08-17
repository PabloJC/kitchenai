package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.shared.domain.usecase.shopping.ObserveShoppingItemsUseCase

/**
 * What the shopping screen watches: the lines of the list it is showing, and the ingredient
 * catalogue behind the add field. Grouped so the ViewModel takes one collaborator per role
 * rather than one parameter per use case; it holds no logic and decides nothing.
 */
class ShoppingReadsDelegate(
    val items: ObserveShoppingItemsUseCase,
    val ingredients: ObserveIngredientsUseCase,
    /** Units are terms, not ingredients: without these a quantity renders as a bare identifier. */
    val taxonomies: ObserveTaxonomiesUseCase,
    val taxonomy: ObserveTaxonomyUseCase,
)
