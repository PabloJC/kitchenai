package com.kitchenai.shared.domain.usecase.profile

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.port.TaxonomyPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveTaxonomyTest {
    private val first = term(termRef("t1", "a"), order = 1)
    private val second = term(termRef("t1", "b"), order = 2)

    @Test
    fun `terms arrive in the order the catalogue declares`() =
        runTest {
            val useCase = ObserveTaxonomy(StubTaxonomyPort(AppResult.Success(listOf(second, first))))

            useCase(first.ref.taxonomy).test {
                assertEquals(AppResult.Success(listOf(first, second)), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a failure reaches the caller untouched`() =
        runTest {
            val error = AppError.Network()
            val useCase = ObserveTaxonomy(StubTaxonomyPort(AppResult.Failure(error)))

            useCase(first.ref.taxonomy).test {
                assertEquals(AppResult.Failure(error), awaitItem())
                awaitComplete()
            }
        }

    private fun term(
        ref: TermRef,
        order: Int,
    ) = Term(ref, labels = mapOf("xx" to "label-${ref.term.value}"), parent = null, order = order)
}

private class StubTaxonomyPort(
    private val terms: AppResult<List<Term>>,
) : TaxonomyPort {
    override fun observeTaxonomy(id: TaxonomyId): Flow<AppResult<List<Term>>> = flowOf(terms)

    override fun observeTaxonomies(): Flow<AppResult<List<Taxonomy>>> = flowOf(AppResult.Success(emptyList()))
}
