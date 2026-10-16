package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItemsUseCase
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.profile.noUnits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlanCookUseCaseTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val unit = termRef("term-a")

    @Test
    fun `a plan lists each holding before and after and writes nothing`() =
        runTest {
            val rice = pantryItem("item-1", "ing-1", Quantity(500.0, unit))
            val pantry = FakePantryRepositoryContract(listOf(rice))
            val dish =
                recipe(servings = 2, ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(200.0, unit))))

            val plan = (planCook(pantry)(kitchen, dish, servings = 2) as AppResult.Success).data

            val deduction = plan.deductions.single()
            assertEquals(rice, deduction.before)
            assertEquals(Quantity(300.0, unit), deduction.after.quantity)
            assertEquals(Quantity(200.0, unit), deduction.taken)
            assertTrue(plan.isComplete)
            assertEquals(0, pantry.upsertAllCalls)
            assertEquals(Quantity(500.0, unit), pantry.items.single().quantity)
        }

    @Test
    fun `a plan with a required line missing says so and still lists what is held`() =
        runTest {
            val pantry = FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(500.0, unit))))
            val dish =
                recipe(
                    servings = 2,
                    ingredients =
                        listOf(
                            recipeIngredient("ing-1", quantity = Quantity(200.0, unit)),
                            recipeIngredient("ing-2", quantity = Quantity(1.0, unit)),
                        ),
                )

            val plan = (planCook(pantry)(kitchen, dish, servings = 2) as AppResult.Success).data

            assertFalse(plan.isComplete)
            assertEquals(1, plan.deductions.size)
            assertEquals(1, plan.missing.size)
        }

    @Test
    fun `a failing pantry read fails the plan`() =
        runTest {
            val pantry = FakePantryRepositoryContract(readError = AppError.Network())
            val dish = recipe(ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(200.0, unit))))

            assertTrue(planCook(pantry)(kitchen, dish, servings = 2) is AppResult.Failure)
        }

    private fun planCook(pantry: FakePantryRepositoryContract): PlanCookUseCase =
        PlanCookUseCase(
            pantry,
            ConsumePantryItemsUseCase(
                pantry,
                TimeProvider {
                    now
                },
                noUnits(),
            ),
            TimeProvider { now },
            noUnits(),
        )
}
