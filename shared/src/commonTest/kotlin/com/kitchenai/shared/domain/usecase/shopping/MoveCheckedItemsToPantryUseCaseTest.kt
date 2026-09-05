package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.AddPantryItemUseCase
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveCheckedItemsToPantryUseCaseTest {
    private val list = listId()
    private val time = fixedTime(1_000)
    private val unit = termRef("units", "gram")

    private fun useCase(
        items: FakeShoppingItemRepositoryContract,
        pantry: FakePantryRepositoryContract = FakePantryRepositoryContract(),
    ) = MoveCheckedItemsToPantryUseCase(items, AddPantryItemUseCase(pantry, sequentialIds(), time))

    @Test
    fun `a checked catalogue line with a quantity moves into the pantry and leaves the list`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true))
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(userId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(1, result.data.moved)
            assertEquals(0, result.data.skipped)
            assertEquals(listOf(ingredientId("rice")), pantry.items.map { it.ingredient })
            assertTrue(items.itemsOf(list).isEmpty())
        }

    @Test
    fun `a checked free-text line moves into the pantry as free text`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                shoppingItem("bread", ingredient = null, freeText = "the good bread", quantity = Quantity(1.0))
                    .copy(checked = true),
            )
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(userId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(1, result.data.moved)
            assertEquals("the good bread", pantry.items.single().freeText)
        }

    @Test
    fun `a checked line with no quantity stays on the list and is reported as skipped`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("milk", quantity = null).copy(checked = true))
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(userId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(0, result.data.moved)
            assertEquals(1, result.data.skipped)
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("milk"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `an unchecked line never moves`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)))
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(userId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(0, result.data.moved)
            assertEquals(0, result.data.skipped)
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("rice"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `nothing is removed from the list when the pantry write fails`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(list, shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true))
            val pantry = FakePantryRepositoryContract(writeError = AppError.Network())

            val result = useCase(items, pantry)(userId(), list)

            assertTrue(result is AppResult.Failure)
            assertEquals(listOf("rice"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `the read failure travels back as it is`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract(AppError.Network())

            val result = useCase(items)(userId(), list)

            assertTrue(result is AppResult.Failure)
            assertTrue(result.error is AppError.Network)
        }
}
