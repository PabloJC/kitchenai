package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.ConsumePantryItemsUseCase
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.pantryItemId
import com.kitchenai.shared.domain.usecase.pantry.termRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CookRecipeUseCaseTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val unit = termRef("term-a")
    private val someOfIngredientOne = pantryItem("item-1", "ing-1", Quantity(500.0, unit))

    @Test
    fun `a missing ingredient stops the cook before anything is written`() =
        runTest {
            val dish =
                dishOf(
                    recipeIngredient("ing-1", quantity = Quantity(200.0, unit)),
                    recipeIngredient("ing-2", quantity = Quantity(1.0, unit)),
                )
            val pantry = pantryOf(someOfIngredientOne)

            val result = cook(dish, pantry)(user, dish.id, servings = 2)

            val error = (result as AppResult.Failure).error
            assertTrue(error is AppError.Validation)
            assertEquals("ingredients", error.field)
            assertTrue(error.reason.contains("1"))
            assertEquals(0, pantry.upsertAllCalls)
            assertEquals(0, pantry.upsertCalls)
            assertTrue(pantry.removed.isEmpty())
        }

    @Test
    fun `cooking subtracts exactly the covered quantities in one write`() =
        runTest {
            val dish =
                dishOf(
                    recipeIngredient("ing-1", quantity = Quantity(200.0, unit)),
                    recipeIngredient("ing-2", quantity = Quantity(1.0)),
                )
            val pantry = pantryOf(someOfIngredientOne, pantryItem("item-2", "ing-2", Quantity(4.0)))

            val result = cook(dish, pantry)(user, dish.id, servings = 2)

            assertTrue(result is AppResult.Success)
            assertEquals(Quantity(300.0, unit), pantry.quantityOf("item-1"))
            assertEquals(Quantity(3.0), pantry.quantityOf("item-2"))
            assertEquals(1, pantry.upsertAllCalls)
        }

    @Test
    fun `what is consumed follows the servings asked for`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(200.0, unit)))
            val pantry = pantryOf(someOfIngredientOne)

            cook(dish, pantry)(user, dish.id, servings = 4)

            assertEquals(Quantity(100.0, unit), pantry.quantityOf("item-1"))
        }

    @Test
    fun `an optional ingredient nobody holds does not stop the cook`() =
        runTest {
            val dish =
                dishOf(
                    recipeIngredient("ing-1", quantity = Quantity(200.0, unit)),
                    recipeIngredient("ing-2", quantity = Quantity(1.0, unit), optional = true),
                )
            val pantry = pantryOf(someOfIngredientOne)

            val result = cook(dish, pantry)(user, dish.id, servings = 2)

            assertTrue(result is AppResult.Success)
            assertEquals(Quantity(300.0, unit), pantry.quantityOf("item-1"))
        }

    @Test
    fun `a line that asks for no amount consumes nothing`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1"))
            val pantry = pantryOf(someOfIngredientOne)

            val result = cook(dish, pantry)(user, dish.id, servings = 2)

            assertTrue(result is AppResult.Success)
            assertEquals(Quantity(500.0, unit), pantry.quantityOf("item-1"))
            assertEquals(0, pantry.upsertAllCalls)
        }

    @Test
    fun `a dish no repository has ever heard of cooks when the caller hands it over`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(200.0, unit)))
            val pantry = pantryOf(someOfIngredientOne)
            // An empty catalogue is a generated dish exactly: its id was minted on this device.
            val useCase =
                CookRecipeUseCase(
                    FakeRecipeRepositoryContract(),
                    pantry,
                    ConsumePantryItemsUseCase(pantry, TimeProvider { now }),
                    TimeProvider { now },
                )

            assertTrue(useCase(user, dish.id, servings = 2) is AppResult.Failure)
            assertTrue(useCase(user, dish, servings = 2) is AppResult.Success)
            assertEquals(Quantity(300.0, unit), pantry.quantityOf("item-1"))
        }

    @Test
    fun `a failing pantry read is reported and never reads as an empty pantry`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(200.0, unit)))
            val pantry = FakePantryRepositoryContract(readError = AppError.Network())

            assertTrue(cook(dish, pantry)(user, dish.id, servings = 2) is AppResult.Failure)
        }

    // Two servings, so that asking for four is a doubling and not the recipe as it stands.
    private fun dishOf(vararg lines: RecipeIngredient): Recipe = recipe(servings = 2, ingredients = lines.toList())

    private fun pantryOf(vararg held: PantryItem): FakePantryRepositoryContract =
        FakePantryRepositoryContract(held.toList())

    private fun cook(
        dish: Recipe,
        pantry: FakePantryRepositoryContract,
    ): CookRecipeUseCase =
        CookRecipeUseCase(
            FakeRecipeRepositoryContract(catalogue = listOf(dish)),
            pantry,
            ConsumePantryItemsUseCase(pantry, TimeProvider { now }),
            TimeProvider { now },
        )

    private fun FakePantryRepositoryContract.quantityOf(id: String): Quantity =
        items.first { it.id == pantryItemId(id) }.quantity
}
