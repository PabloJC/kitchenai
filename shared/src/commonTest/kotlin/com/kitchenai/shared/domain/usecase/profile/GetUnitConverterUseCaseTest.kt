package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.shopping.termRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetUnitConverterUseCaseTest {
    private val gram = termRef("units", "gram")
    private val kilogram = termRef("units", "kilogram")

    @Test
    fun `builds a converter from the units taxonomy`() =
        runTest {
            val useCase = GetUnitConverterUseCase(FakeTaxonomyRepositoryContract(metricUnitTerms("units")))

            val converter = (useCase() as AppResult.Success).data

            assertEquals(0.5, converter.amountIn(Quantity(500.0, gram), kilogram))
        }

    @Test
    fun `a catalogue with no units taxonomy converts nothing instead of failing`() =
        runTest {
            val converter = (noUnits()() as AppResult.Success).data

            assertEquals(null, converter.amountIn(Quantity(500.0, gram), kilogram))
            assertEquals(500.0, converter.amountIn(Quantity(500.0, gram), gram))
        }

    @Test
    fun `a failing catalogue read is reported`() =
        runTest {
            val error = AppError.Network()
            val useCase =
                GetUnitConverterUseCase(
                    FakeTaxonomyRepositoryContract(metricUnitTerms("units"), taxonomiesError = error),
                )

            assertEquals(AppResult.Failure(error), useCase())
        }

    @Test
    fun `a failing terms read is reported and never reads as a converter that converts nothing`() =
        runTest {
            val error = AppError.Timeout()
            val useCase =
                GetUnitConverterUseCase(FakeTaxonomyRepositoryContract(metricUnitTerms("units"), termsError = error))

            val result = useCase()

            assertTrue(result is AppResult.Failure)
            assertEquals(error, result.error)
        }
}
