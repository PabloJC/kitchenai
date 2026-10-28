package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.freeTextPantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.profile.noUnits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddPantryItemsToShoppingListUseCaseTest {
    private val kitchen = kitchenId()
    private val labels = mapOf("en" to "My list")
    private val eggs = pantryItem("held-eggs", "eggs", Quantity(6.0))
    private val bread = freeTextPantryItem("held-bread", "the good bread", Quantity(1.0))

    private fun useCase(
        lists: FakeShoppingListRepositoryContract = FakeShoppingListRepositoryContract(),
        items: FakeShoppingItemRepositoryContract = FakeShoppingItemRepositoryContract(),
    ) = AddPantryItemsToShoppingListUseCase(
        EnsureDefaultShoppingListUseCase(lists, sequentialIds(), fixedTime(1_000)),
        AddShoppingItemUseCase(items, sequentialIds(), fixedTime(1_000), noUnits()),
    )

    @Test
    fun `each row goes on the default list as a bare line of the same thing`() =
        runTest {
            val lists = FakeShoppingListRepositoryContract()
            val items = FakeShoppingItemRepositoryContract()

            val result = useCase(lists, items)(kitchen, labels, listOf(eggs, bread))

            assertEquals(AppResult.Success(2), result)
            val list = lists.observeLists(kitchen).first().single()
            val lines = items.itemsOf(list.id)
            assertEquals(listOf(ingredientId("eggs"), null), lines.map { it.ingredient })
            assertEquals(listOf(null, "the good bread"), lines.map { it.freeText })
            assertTrue(lines.all { it.quantity == null })
        }

    @Test
    fun `nothing to add creates no list`() =
        runTest {
            val lists = FakeShoppingListRepositoryContract()

            assertEquals(AppResult.Success(0), useCase(lists)(kitchen, labels, emptyList()))
            assertEquals(0, lists.upsertListCalls)
        }

    @Test
    fun `a list that cannot be read stops before adding anything`() =
        runTest {
            val items = FakeShoppingItemRepositoryContract()

            val result =
                useCase(FakeShoppingListRepositoryContract(AppError.Network()), items)(kitchen, labels, listOf(eggs))

            assertTrue(result is AppResult.Failure)
            assertEquals(0, items.upsertCalls)
        }

    @Test
    fun `a line that cannot be written travels back as it is`() =
        runTest {
            val result =
                useCase(
                    items = FakeShoppingItemRepositoryContract(AppError.Unauthorized()),
                )(kitchen, labels, listOf(eggs))

            assertTrue(result is AppResult.Failure)
        }
}
