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
 * nothing to name a [Term] with — so the failure travels on its own error stream, keyed the
 * same way as the observer, not per emission.
 */
interface TaxonomyPort {
    fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>>

    fun observeTaxonomies(): Flow<List<Taxonomy>>

    /** Failures of the listeners above, keyed like them: a broken taxonomy is not every one. */
    fun taxonomyErrors(id: TaxonomyId): Flow<AppError>

    fun taxonomiesErrors(): Flow<AppError>

    /** One-shot read: validating a profile cannot depend on a listener that may have failed. */
    suspend fun getTaxonomies(): AppResult<List<Taxonomy>>
}
