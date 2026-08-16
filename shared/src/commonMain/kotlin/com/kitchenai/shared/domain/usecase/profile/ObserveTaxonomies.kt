package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import kotlinx.coroutines.flow.Flow

/**
 * Streams which vocabularies exist, in the catalogue's own order.
 *
 * It is what decides how many sections a preferences screen has: nothing in code may know that
 * a particular taxonomy is there, so something has to say it, and this is that something.
 */
class ObserveTaxonomies(
    private val taxonomies: TaxonomyRepositoryContract,
) {
    operator fun invoke(): Flow<List<Taxonomy>> = taxonomies.observeTaxonomies()

    /** The listener's failures, collected alongside the stream above. */
    fun errors(): Flow<AppError> = taxonomies.taxonomiesErrors()
}
