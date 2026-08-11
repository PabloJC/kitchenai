package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the vocabulary catalogue.
 *
 * The streams carry [AppResult] because a catalogue that failed to load is a state the UI
 * has to render: without it there is nothing to name a [Term] with.
 */
interface TaxonomyPort {
    fun observeTaxonomy(id: TaxonomyId): Flow<AppResult<List<Term>>>

    fun observeTaxonomies(): Flow<AppResult<List<Taxonomy>>>
}
