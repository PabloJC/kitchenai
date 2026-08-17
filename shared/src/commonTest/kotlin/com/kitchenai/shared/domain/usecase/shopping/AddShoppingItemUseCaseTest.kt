package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddShoppingItemUseCaseTest {
    private val list = listId()
    private val user = userId()
    private val grams = termRef("unit", "gram")
    private val millilitres = termRef("unit", "millilitre")
    private val port = FakeShoppingItemRepositoryContract()
    private val useCase = AddShoppingItemUseCase(port, sequentialIds(), fixedTime(2_000))

    @Test
    fun `the same ingredient in the same unit merges into one line`() =
        runTest {
            port.seed(list, shoppingItem("flour", quantity = Quantity(200.0, grams)))

            val result = useCase(user, list, ingredient = ingredientId("flour"), quantity = Quantity(300.0, grams))

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(Quantity(500.0, grams)), port.itemsOf(list).map { it.quantity })
        }

    @Test
    fun `the same ingredient in another unit opens a second line rather than converting`() =
        runTest {
            port.seed(list, shoppingItem("flour", quantity = Quantity(200.0, grams)))

            useCase(user, list, ingredient = ingredientId("flour"), quantity = Quantity(300.0, millilitres))

            assertEquals(2, port.itemsOf(list).size)
        }

    @Test
    fun `a free-text line never merges with an identical one`() =
        runTest {
            useCase(user, list, freeText = "the good bread")
            useCase(user, list, freeText = "the good bread")

            assertEquals(2, port.itemsOf(list).size)
        }

    @Test
    fun `a checked line is left alone and the ingredient is added again`() =
        runTest {
            port.seed(list, shoppingItem("flour", quantity = Quantity(200.0, grams)).copy(checked = true))

            useCase(user, list, ingredient = ingredientId("flour"), quantity = Quantity(300.0, grams))

            assertEquals(2, port.itemsOf(list).size)
        }

    @Test
    fun `a line with neither an ingredient nor free text is not stored`() =
        runTest {
            val result = useCase(user, list)

            assertTrue(result is AppResult.Failure)
            assertTrue(port.itemsOf(list).isEmpty())
        }
}
