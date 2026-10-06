package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.profile.FakeTaxonomyRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.GetUnitConverterUseCase
import com.kitchenai.shared.domain.usecase.profile.metricUnitTerms
import com.kitchenai.shared.domain.usecase.profile.metricUnits
import com.kitchenai.shared.domain.usecase.profile.noUnits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddShoppingItemUseCaseTest {
    private val list = listId()
    private val kitchen = kitchenId()
    private val grams = termRef("unit", "gram")
    private val millilitres = termRef("unit", "millilitre")
    private val kilograms = termRef("unit", "kilogram")
    private val port = FakeShoppingItemRepositoryContract()
    private val useCase = AddShoppingItemUseCase(port, sequentialIds(), fixedTime(2_000), noUnits())

    @Test
    fun `the same ingredient in the same unit merges into one line`() =
        runTest {
            port.seed(list, shoppingItem("flour", quantity = Quantity(200.0, grams)))

            val result = useCase(kitchen, list, ingredient = ingredientId("flour"), quantity = Quantity(300.0, grams))

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(Quantity(500.0, grams)), port.itemsOf(list).map { it.quantity })
        }

    @Test
    fun `the same ingredient in another unit opens a second line rather than converting`() =
        runTest {
            port.seed(list, shoppingItem("flour", quantity = Quantity(200.0, grams)))

            useCase(kitchen, list, ingredient = ingredientId("flour"), quantity = Quantity(300.0, millilitres))

            assertEquals(2, port.itemsOf(list).size)
        }

    @Test
    fun `a free-text line never merges with an identical one`() =
        runTest {
            useCase(kitchen, list, freeText = "the good bread")
            useCase(kitchen, list, freeText = "the good bread")

            assertEquals(2, port.itemsOf(list).size)
        }

    @Test
    fun `a checked line is left alone and the ingredient is added again`() =
        runTest {
            port.seed(list, shoppingItem("flour", quantity = Quantity(200.0, grams)).copy(checked = true))

            useCase(kitchen, list, ingredient = ingredientId("flour"), quantity = Quantity(300.0, grams))

            assertEquals(2, port.itemsOf(list).size)
        }

    @Test
    fun `a line with neither an ingredient nor free text is not stored`() =
        runTest {
            val result = useCase(kitchen, list)

            assertTrue(result is AppResult.Failure)
            assertTrue(port.itemsOf(list).isEmpty())
        }

    @Test
    fun `the same ingredient in a convertible unit merges into the line in its own unit`() =
        runTest {
            val converting = AddShoppingItemUseCase(port, sequentialIds(), fixedTime(2_000), metricUnits("unit"))
            port.seed(list, shoppingItem("flour", quantity = Quantity(500.0, grams)))

            val result =
                converting(kitchen, list, ingredient = ingredientId("flour"), quantity = Quantity(1.0, kilograms))

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(Quantity(1500.0, grams)), port.itemsOf(list).map { it.quantity })
        }

    @Test
    fun `a convertible unit of another dimension still opens a second line`() =
        runTest {
            val converting = AddShoppingItemUseCase(port, sequentialIds(), fixedTime(2_000), metricUnits("unit"))
            port.seed(list, shoppingItem("flour", quantity = Quantity(500.0, grams)))

            converting(kitchen, list, ingredient = ingredientId("flour"), quantity = Quantity(1.0, millilitres))

            assertEquals(2, port.itemsOf(list).size)
        }

    @Test
    fun `a converter that cannot be read fails the add and writes nothing`() =
        runTest {
            val broken =
                GetUnitConverterUseCase(
                    FakeTaxonomyRepositoryContract(metricUnitTerms("unit"), taxonomiesError = AppError.Network()),
                )

            val result =
                AddShoppingItemUseCase(port, sequentialIds(), fixedTime(2_000), broken)(
                    kitchen,
                    list,
                    ingredient = ingredientId("flour"),
                    quantity = Quantity(1.0, grams),
                )

            assertTrue(result is AppResult.Failure)
            assertEquals(0, port.upsertCalls)
        }
}
