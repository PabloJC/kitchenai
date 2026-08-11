package com.kitchenai.shared.domain.model

/**
 * An opaque pointer into a taxonomy: a diet, an allergen, a cuisine, a unit, a storage place.
 *
 * It carries no label, no description and no icon on purpose. Naming a term in code would
 * couple the domain to one market and turn adding a term into a release; labels belong to
 * the taxonomy catalogue and are resolved for display only.
 */
data class TermRef(
    val taxonomy: TaxonomyId,
    val term: TermId,
)
