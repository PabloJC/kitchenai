package com.kitchenai.shared.domain.usecase.pantry

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.profile.FakeTaxonomyRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import com.kitchenai.shared.domain.usecase.profile.metricUnitTerms
import com.kitchenai.shared.domain.usecase.profile.metricUnits
import com.kitchenai.shared.domain.usecase.profile.noUnits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ConsumePantryItemsUseCaseTest {
    private val unitA = termRef("term-a")
    private val unitB = termRef("term-b")
    private val port =
        FakePantryRepositoryContract(
            listOf(
                pantryItem("item-1", "ing-1", Quantity(200.0, unitA)),
                pantryItem("item-2", "ing-2", Quantity(4.0)),
            ),
        )
    private val useCase = ConsumePantryItemsUseCase(port, TimeProvider { Instant.fromEpochSeconds(1_000) }, noUnits())

    private fun quantityOf(id: String) = port.items.first { it.id == pantryItemId(id) }.quantity

    @Test
    fun `subtracts every consumption in a single batched write`() =
        runTest {
            val result =
                useCase(
                    kitchen,
                    listOf(pantryItemId("item-1") to Quantity(50.0, unitA), pantryItemId("item-2") to Quantity(1.0)),
                )

            assertTrue(result is AppResult.Success)
            assertEquals(1, port.upsertAllCalls)
            assertEquals(Quantity(150.0, unitA), quantityOf("item-1"))
            assertEquals(Quantity(3.0), quantityOf("item-2"))
        }

    @Test
    fun `removes a holding consumed down to exactly zero`() =
        runTest {
            val result = useCase(kitchen, listOf(pantryItemId("item-2") to Quantity(4.0)))

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(pantryItemId("item-2")), port.removed)
            assertTrue(port.items.none { it.id == pantryItemId("item-2") })
        }

    @Test
    fun `over-consumption fails as a validation error and writes nothing`() =
        runTest {
            val result = useCase(kitchen, listOf(pantryItemId("item-1") to Quantity(500.0, unitA)))

            assertTrue(result is AppResult.Failure)
            assertEquals("amount", (result.error as AppError.Validation).field)
            assertEquals(0, port.upsertAllCalls)
            assertEquals(Quantity(200.0, unitA), quantityOf("item-1"))
        }

    @Test
    fun `a negative consumption fails instead of adding to the holding`() =
        runTest {
            val result = useCase(kitchen, listOf(pantryItemId("item-1") to Quantity(-50.0, unitA)))

            assertTrue(result is AppResult.Failure)
            assertEquals("amount", (result.error as AppError.Validation).field)
            assertEquals(0, port.upsertAllCalls)
            assertEquals(Quantity(200.0, unitA), quantityOf("item-1"))
        }

    @Test
    fun `a consumption in another unit fails instead of being converted`() =
        runTest {
            val result = useCase(kitchen, listOf(pantryItemId("item-1") to Quantity(1.0, unitB)))

            assertTrue(result is AppResult.Failure)
            assertEquals("unit", (result.error as AppError.Validation).field)
            assertEquals(0, port.upsertAllCalls)
        }

    @Test
    fun `a consumption of something not held fails as NotFound`() =
        runTest {
            val result = useCase(kitchen, listOf(pantryItemId("item-9") to Quantity(1.0)))

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.NotFound)
            assertEquals(0, port.upsertAllCalls)
        }

    @Test
    fun `a consumption in a convertible unit is subtracted in the unit the holding is kept in`() =
        runTest {
            val held =
                FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(500.0, termRef("gram")))))

            val result = converting(held)(kitchen, listOf(pantryItemId("item-1") to Quantity(0.2, termRef("kilogram"))))

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(Quantity(300.0, termRef("gram"))), held.items.map { it.quantity })
        }

    @Test
    fun `consuming a holding down to the same amount in another unit removes it`() =
        runTest {
            // 4.03 kg is 4030.0000000000005 g: without settling, this would be over-consumption.
            val held =
                FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(4030.0, termRef("gram")))))

            val result =
                converting(held)(kitchen, listOf(pantryItemId("item-1") to Quantity(4.03, termRef("kilogram"))))

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(pantryItemId("item-1")), held.removed)
        }

    @Test
    fun `over-consumption across units still fails and writes nothing`() =
        runTest {
            val held =
                FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(500.0, termRef("gram")))))

            val result = converting(held)(kitchen, listOf(pantryItemId("item-1") to Quantity(0.6, termRef("kilogram"))))

            assertTrue(result is AppResult.Failure)
            assertEquals("amount", (result.error as AppError.Validation).field)
            assertEquals(0, held.upsertAllCalls)
        }

    @Test
    fun `a consumption in a unit of another dimension still fails even with a converter`() =
        runTest {
            val held =
                FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(500.0, termRef("gram")))))

            val result = converting(held)(kitchen, listOf(pantryItemId("item-1") to Quantity(1.0, termRef("litre"))))

            assertTrue(result is AppResult.Failure)
            assertEquals("unit", (result.error as AppError.Validation).field)
        }

    @Test
    fun `a converter that cannot be read fails the consumption before touching the pantry`() =
        runTest {
            val broken =
                GetUnitConverterUseCase(
                    FakeTaxonomyRepositoryContract(metricUnitTerms("taxonomy-1"), taxonomiesError = AppError.Network()),
                )

            val result =
                ConsumePantryItemsUseCase(port, TimeProvider { Instant.fromEpochSeconds(1_000) }, broken)(
                    kitchen,
                    listOf(pantryItemId("item-1") to Quantity(50.0, unitA)),
                )

            assertTrue(result is AppResult.Failure)
            assertEquals(0, port.upsertAllCalls)
            assertEquals(Quantity(200.0, unitA), quantityOf("item-1"))
        }

    private fun converting(pantry: FakePantryRepositoryContract) =
        ConsumePantryItemsUseCase(pantry, TimeProvider { Instant.fromEpochSeconds(1_000) }, metricUnits("taxonomy-1"))
}
