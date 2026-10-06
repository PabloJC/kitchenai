package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import com.kitchenai.shared.domain.service.UnitConverter

/**
 * Builds a [UnitConverter] from the taxonomy whose purpose is [TaxonomyPurpose.UNITS], with
 * one-shot reads. A catalogue with no units taxonomy yields a converter that converts nothing.
 */
class GetUnitConverterUseCase(
    private val taxonomies: TaxonomyRepositoryContract,
) {
    suspend operator fun invoke(): AppResult<UnitConverter> =
        taxonomies.getTaxonomies().flatMap { catalogue ->
            val units = catalogue.firstOrNull { it.purpose == TaxonomyPurpose.UNITS }
            if (units == null) {
                AppResult.Success(UnitConverter.NONE)
            } else {
                taxonomies.getTerms(units.id).map { terms -> UnitConverter(terms) }
            }
        }
}
