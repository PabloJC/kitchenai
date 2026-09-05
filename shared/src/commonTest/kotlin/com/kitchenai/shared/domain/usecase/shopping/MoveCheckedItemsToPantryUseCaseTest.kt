package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
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
    ) = MoveCheckedItemsToPantryUseCase(items, pantry, sequentialIds(), time)

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
    fun `two checked lines for the same ingredient in the same unit merge with each other`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                shoppingItem("rice-1", "rice", quantity = Quantity(100.0, unit)).copy(checked = true),
                shoppingItem("rice-2", "rice", quantity = Quantity(150.0, unit)).copy(checked = true),
            )
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(userId(), list)

            assertTrue(result is AppResult.Success)
            assertEquals(2, result.data.moved)
            val holding = pantry.items.single()
            assertEquals(Quantity(250.0, unit), holding.quantity)
        }

    @Test
    fun `nothing is written to the pantry or removed from the list when a later line fails`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                // Would succeed on its own — the point is that it must not be written just
                // because it was processed before the line that fails.
                shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true),
                // Both an ingredient and free text: rejected by draftPantryHolding by way of
                // PantryItem.create's own invariant.
                shoppingItem("broken", quantity = Quantity(1.0))
                    .copy(checked = true, freeText = "not allowed alongside an ingredient"),
            )
            val pantry = FakePantryRepositoryContract()

            val result = useCase(items, pantry)(userId(), list)

            assertTrue(result is AppResult.Failure)
            // The earlier, individually-valid line must not have been committed on its own.
            assertTrue(pantry.items.isEmpty())
            assertEquals(listOf("rice", "broken"), items.itemsOf(list).map { it.id.value })
        }

    @Test
    fun `a pantry write failure leaves the list exactly as it was`() =
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
