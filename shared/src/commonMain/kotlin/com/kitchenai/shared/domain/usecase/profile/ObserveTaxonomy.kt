package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.port.TaxonomyPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Streams the terms of one taxonomy in the catalogue's own order, because sorting by label
 * would depend on a locale the domain does not know.
 */
class ObserveTaxonomy(
    private val taxonomies: TaxonomyPort,
) {
    operator fun invoke(id: TaxonomyId): Flow<AppResult<List<Term>>> =
        taxonomies.observeTaxonomy(id).map { result -> result.map { terms -> terms.sortedBy(Term::order) } }
}
