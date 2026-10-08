package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UnitConversion
import com.kitchenai.shared.domain.model.UnitDimension
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import com.kitchenai.shared.domain.service.UnitConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [TaxonomyRepositoryContract] holding one `UNITS` taxonomy made of [units], or none
 * at all when it is empty, plus any [others] as given. The two one-shot reads fail independently, so both error branches of
 * a caller can be reached.
 */
class FakeTaxonomyRepositoryContract(
    private val units: List<Term> = emptyList(),
    private val taxonomiesError: AppError? = null,
    private val termsError: AppError? = null,
    private val others: List<Taxonomy> = emptyList(),
) : TaxonomyRepositoryContract {
    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> = flowOf(units.filter { it.ref.taxonomy == id })

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = flowOf(catalogue())

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = emptyFlow()

    override fun taxonomiesErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> =
        taxonomiesError?.let { AppResult.Failure(it) } ?: AppResult.Success(catalogue())

    override suspend fun getTerms(id: TaxonomyId): AppResult<List<Term>> =
        termsError?.let { AppResult.Failure(it) } ?: AppResult.Success(units.filter { it.ref.taxonomy == id })

    private fun catalogue(): List<Taxonomy> =
        units.firstOrNull()?.let {
            listOf(
                Taxonomy(it.ref.taxonomy, emptyMap(), purpose = TaxonomyPurpose.UNITS),
            )
        }.orEmpty() + others
}

/** A unit term, with no conversion data unless [dimension] and [factor] say so. */
fun unitTerm(
    taxonomy: String,
    term: String,
    dimension: UnitDimension? = null,
    factor: Double = 1.0,
): Term =
    Term(
        ref = TermRef((TaxonomyId.of(taxonomy) as AppResult.Success).data, (TermId.of(term) as AppResult.Success).data),
        labels = emptyMap(),
        parent = null,
        order = 0,
        conversion = dimension?.let { UnitConversion(it, factor) },
    )

/** The seed's units: grams and kilos, millilitres, litres and spoons, plus a piece that converts to nothing. */
fun metricUnitTerms(taxonomy: String): List<Term> =
    listOf(
        unitTerm(taxonomy, "gram", UnitDimension.MASS, 1.0),
        unitTerm(taxonomy, "kilogram", UnitDimension.MASS, 1000.0),
        unitTerm(taxonomy, "millilitre", UnitDimension.VOLUME, 1.0),
        unitTerm(taxonomy, "litre", UnitDimension.VOLUME, 1000.0),
        unitTerm(taxonomy, "tablespoon", UnitDimension.VOLUME, 15.0),
        unitTerm(taxonomy, "teaspoon", UnitDimension.VOLUME, 5.0),
        unitTerm(taxonomy, "piece"),
    )

fun metricUnitConverter(taxonomy: String): UnitConverter = UnitConverter(metricUnitTerms(taxonomy))

/** A use case over the metric units of [taxonomy], for tests that convert. */
fun metricUnits(taxonomy: String): GetUnitConverterUseCase =
    GetUnitConverterUseCase(FakeTaxonomyRepositoryContract(metricUnitTerms(taxonomy)))

/** A use case over a catalogue with no units at all, for tests that expect nothing to convert. */
fun noUnits(): GetUnitConverterUseCase = GetUnitConverterUseCase(FakeTaxonomyRepositoryContract())
