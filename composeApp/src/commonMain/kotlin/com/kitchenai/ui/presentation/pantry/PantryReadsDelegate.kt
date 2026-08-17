package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase

/**
 * What the pantry screen watches: the holdings, the ingredient catalogue and the unit
 * vocabulary. Grouped so the ViewModel takes one collaborator per role rather than one
 * parameter per use case; it holds no logic and decides nothing.
 */
class PantryReadsDelegate(
    val pantry: ObservePantryUseCase,
    val ingredients: ObserveIngredientsUseCase,
    val taxonomy: ObserveTaxonomyUseCase,
    // The terms carry the words; the taxonomies carry the language each one falls back to.
    val taxonomies: ObserveTaxonomiesUseCase,
)
