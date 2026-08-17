package com.kitchenai.shared.domain.usecase.profile

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveTaxonomyUseCaseTest {
    private val first = term(termRef("t1", "a"), order = 1)
    private val second = term(termRef("t1", "b"), order = 2)

    @Test
    fun `terms arrive in the order the catalogue declares`() =
        runTest {
            val useCase = ObserveTaxonomyUseCase(StubTaxonomyPort(listOf(second, first)))

            useCase(first.ref.taxonomy).test {
                assertEquals(listOf(first, second), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a failing listener reports on errors and emits no terms`() =
        runTest {
            val error = AppError.Network()
            val useCase = ObserveTaxonomyUseCase(StubTaxonomyPort(failure = error))

            useCase(first.ref.taxonomy).test { awaitComplete() }
            useCase.errors(first.ref.taxonomy).test {
                assertEquals(error, awaitItem())
                awaitComplete()
            }
        }

    private fun term(
        ref: TermRef,
        order: Int,
    ) = Term(ref, labels = mapOf("xx" to "label-${ref.term.value}"), parent = null, order = order)
}

private class StubTaxonomyPort(
    private val terms: List<Term> = emptyList(),
    private val failure: AppError? = null,
) : TaxonomyRepositoryContract {
    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> = if (failure == null) flowOf(terms) else emptyFlow()

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = flowOf(emptyList())

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = failure?.let { flowOf(it) } ?: emptyFlow()

    override fun taxonomiesErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> = AppResult.Success(emptyList())
}
