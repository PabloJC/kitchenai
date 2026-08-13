package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredients
import com.kitchenai.shared.domain.usecase.pantry.ObservePantry
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomies
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomy

/**
 * What the pantry screen watches: the holdings, the ingredient catalogue and the unit
 * vocabulary. Grouped so the ViewModel takes one collaborator per role rather than one
 * parameter per use case; it holds no logic and decides nothing.
 */
class PantryReads(
    val pantry: ObservePantry,
    val ingredients: ObserveIngredients,
    val taxonomy: ObserveTaxonomy,
    // The terms carry the words; the taxonomies carry the language each one falls back to.
    val taxonomies: ObserveTaxonomies,
)
