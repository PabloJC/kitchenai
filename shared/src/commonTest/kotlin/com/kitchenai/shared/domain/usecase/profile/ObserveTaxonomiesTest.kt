package com.kitchenai.shared.domain.usecase.profile

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.port.TaxonomyPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveTaxonomiesTest {
    private val catalogue =
        listOf(
            Taxonomy(taxonomyId("t2"), labels = mapOf("xx" to "second")),
            Taxonomy(taxonomyId("t1"), labels = mapOf("xx" to "first")),
        )

    @Test
    fun `the catalogue arrives in the order it was published`() =
        runTest {
            val useCase = ObserveTaxonomies(StubCataloguePort(catalogue))

            useCase().test {
                assertEquals(catalogue, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a failing listener reports on errors and publishes no catalogue`() =
        runTest {
            val error = AppError.Network()
            val useCase = ObserveTaxonomies(StubCataloguePort(failure = error))

            useCase().test { awaitComplete() }
            useCase.errors().test {
                assertEquals(error, awaitItem())
                awaitComplete()
            }
        }

    private fun taxonomyId(raw: String): TaxonomyId = (TaxonomyId.of(raw) as AppResult.Success).data
}

private class StubCataloguePort(
    private val catalogue: List<Taxonomy> = emptyList(),
    private val failure: AppError? = null,
) : TaxonomyPort {
    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> = emptyFlow()

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = if (failure == null) flowOf(catalogue) else emptyFlow()

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = emptyFlow()

    override fun taxonomiesErrors(): Flow<AppError> = failure?.let { flowOf(it) } ?: emptyFlow()

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> = AppResult.Success(catalogue)
}
