package com.kitchenai.shared.domain.port

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the vocabulary catalogue.
 *
 * A catalogue that failed to load is a state the UI has to render — without it there is
 * nothing to name a [Term] with — so the failure travels on [streamErrors], not per emission.
 */
interface TaxonomyPort {
    fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>>

    fun observeTaxonomies(): Flow<List<Taxonomy>>

    /** Failures of the listeners above, which stop emitting rather than throwing. */
    fun streamErrors(): Flow<AppError>

    /** One-shot read: validating a profile cannot depend on a listener that may have failed. */
    suspend fun getTaxonomies(): AppResult<List<Taxonomy>>
}
