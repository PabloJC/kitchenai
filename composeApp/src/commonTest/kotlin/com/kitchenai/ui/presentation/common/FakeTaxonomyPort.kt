package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.port.TaxonomyPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** The vocabulary, shared by every screen that resolves an identifier into a word. */
class FakeTaxonomyPort : TaxonomyPort {
    val terms = MutableSharedFlow<List<Term>>(replay = 1)

    // Replay without an initial value: a listener that has not answered emits nothing, and a
    // fake that emits an empty catalogue on subscribe hides the difference.
    val taxonomies = MutableSharedFlow<List<Taxonomy>>(replay = 1)

    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> =
        terms.map { known -> known.filter { term -> term.ref.taxonomy == id } }

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = taxonomies

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = emptyFlow()

    override fun taxonomiesErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> = AppResult.Success(emptyList())
}
