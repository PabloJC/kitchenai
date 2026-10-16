package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.CookPlan
import com.kitchenai.shared.domain.model.PantryDeduction
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItemId
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.profile.noUnits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class UndoCookUseCaseTest {
    private val now = Instant.fromEpochSeconds(2_000)
    private val unit = termRef("term-a")
    private val before = pantryItem("item-1", "ing-1", Quantity(500.0, unit))

    @Test
    fun `undo adds what was taken to the holding as it is now`() =
        runTest {
            // Someone re-stocked to 400 after the cook left 300: the undo keeps their 100.
            val pantry = FakePantryRepositoryContract(listOf(before.copy(quantity = Quantity(400.0, unit))))

            val result = undo(pantry)(kitchen, cooked(after = 300.0))

            assertTrue(result is AppResult.Success)
            assertEquals(Quantity(600.0, unit), pantry.items.single().quantity)
            assertEquals(now, pantry.items.single().updatedAt)
        }

    @Test
    fun `a holding the cook emptied comes back as it was`() =
        runTest {
            val pantry = FakePantryRepositoryContract()

            undo(pantry)(kitchen, cooked(after = 0.0))

            val restored = pantry.items.single()
            assertEquals(pantryItemId("item-1"), restored.id)
            assertEquals(Quantity(500.0, unit), restored.quantity)
        }

    @Test
    fun `a cook that took nothing has nothing to undo`() =
        runTest {
            val pantry = FakePantryRepositoryContract()

            assertTrue(undo(pantry)(kitchen, CookPlan(emptyList(), emptyList())) is AppResult.Success)
            assertEquals(0, pantry.upsertAllCalls)
        }

    @Test
    fun `a failing pantry read fails the undo and writes nothing`() =
        runTest {
            val pantry = FakePantryRepositoryContract(readError = AppError.Network())

            assertTrue(undo(pantry)(kitchen, cooked(after = 300.0)) is AppResult.Failure)
            assertEquals(0, pantry.upsertAllCalls)
        }

    private fun cooked(after: Double): CookPlan =
        CookPlan(
            deductions = listOf(PantryDeduction(before, before.copy(quantity = Quantity(after, unit)))),
            missing = emptyList(),
        )

    private fun undo(pantry: FakePantryRepositoryContract): UndoCookUseCase =
        UndoCookUseCase(pantry, TimeProvider { now }, noUnits())
}
