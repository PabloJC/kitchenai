package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.MoveToPantryOptions
import com.kitchenai.shared.domain.model.PantryMoveRecord
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItemId
import com.kitchenai.shared.domain.usecase.profile.noUnits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UndoMoveToPantryUseCaseTest {
    private val list = listId()
    private val unit = termRef("units", "gram")
    private val heldRice = pantryItem("held-rice", "rice", Quantity(500.0, unit))

    @Test
    fun `undo puts back the lines and the merged amount and removes the row the move created`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            val rice = shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true)
            val beans = shoppingItem("beans", quantity = Quantity(300.0, unit)).copy(checked = true)
            items.seed(list, rice, beans)
            val pantry = FakePantryRepositoryContract(listOf(heldRice))
            val moved = move(items, pantry)
            assertEquals(setOf(Quantity(700.0, unit), Quantity(300.0, unit)), pantry.items.map { it.quantity }.toSet())

            val result = UndoMoveToPantryUseCase(items, pantry)(kitchenId(), moved)

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(heldRice), pantry.items)
            assertEquals(setOf(rice, beans), items.itemsOf(list).toSet())
        }

    @Test
    fun `undo of a dated move with a bare line puts back the bare line and the undated holding`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            val rice = shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true)
            val milk = shoppingItem("milk", quantity = null).copy(checked = true)
            items.seed(list, rice, milk)
            val pantry = FakePantryRepositoryContract(listOf(heldRice))
            val options =
                MoveToPantryOptions(expiresAt = mapOf(rice.id to instant(9_000)), defaultAmountForBare = true)
            val moved = move(items, pantry, options)
            assertEquals(instant(9_000), pantry.items.first { it.id == heldRice.id }.expiresAt)

            UndoMoveToPantryUseCase(items, pantry)(kitchenId(), moved)

            assertEquals(listOf(heldRice), pantry.items)
            assertEquals(setOf(rice, milk), items.itemsOf(list).toSet())
        }

    @Test
    fun `the move records what it merged into and what it created`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            items.seed(
                list,
                shoppingItem("rice", quantity = Quantity(200.0, unit)).copy(checked = true),
                shoppingItem("beans", quantity = Quantity(300.0, unit)).copy(checked = true),
            )
            val pantry = FakePantryRepositoryContract(listOf(heldRice))

            val record = move(items, pantry)

            assertEquals(listOf(heldRice), record.before)
            assertEquals(pantry.items.map { it.id } - heldRice.id, record.created)
            assertEquals(listOf("rice", "beans"), record.lines.map { it.id.value })
        }

    @Test
    fun `a list that cannot be written leaves the pantry as the move left it`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract(failure = AppError.Unauthorized())
            val merged = heldRice.copy(quantity = Quantity(700.0, unit))
            val pantry = FakePantryRepositoryContract(listOf(merged))
            val record = PantryMoveRecord(list, listOf(shoppingItem("rice")), listOf(heldRice), emptyList())

            val result = UndoMoveToPantryUseCase(items, pantry)(kitchenId(), record)

            assertTrue(result is AppResult.Failure)
            assertEquals(listOf(merged), pantry.items)
        }

    @Test
    fun `a pantry that cannot be written is reported once the lines are already back`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()
            val pantry = FakePantryRepositoryContract(writeError = AppError.Unauthorized())
            val line = shoppingItem("beans", quantity = Quantity(300.0, unit))
            val record = PantryMoveRecord(list, listOf(line), emptyList(), listOf(pantryItemId("generated-1")))

            val result = UndoMoveToPantryUseCase(items, pantry)(kitchenId(), record)

            assertTrue(result is AppResult.Failure)
            assertEquals(listOf(line), items.itemsOf(list))
        }

    private suspend fun move(
        items: FakeShoppingItemRepositoryContract,
        pantry: FakePantryRepositoryContract,
        options: MoveToPantryOptions = MoveToPantryOptions(),
    ): PantryMoveRecord {
        val useCase =
            MoveCheckedItemsToPantryUseCase(
                items,
                pantry,
                sequentialIds(),
                fixedTime(1_000),
                noUnits(),
                FakeIngredientRepositoryContract(),
            )
        val result = useCase(kitchenId(), list, options)
        return checkNotNull((result as AppResult.Success).data.undo)
    }
}
