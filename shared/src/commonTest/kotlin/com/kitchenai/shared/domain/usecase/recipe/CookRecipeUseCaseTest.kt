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

class CookRecipeUseCaseTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val unit = termRef("term-a")
    private val someOfIngredientOne = pantryItem("item-1", "ing-1", Quantity(500.0, unit))
    private val gram = termRef("gram")
    private val kilogram = termRef("kilogram")

    @Test
    fun `a missing ingredient stops the cook before anything is written`() =
        runTest {
            val dish =
                dishOf(
                    recipeIngredient("ing-1", quantity = Quantity(200.0, unit)),
                    recipeIngredient("ing-2", quantity = Quantity(1.0, unit)),
                )
            val pantry = pantryOf(someOfIngredientOne)

            val result = cook(dish, pantry)(kitchen, dish.id, servings = 2)

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

            val result = cook(dish, pantry)(kitchen, dish.id, servings = 2)

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

            cook(dish, pantry)(kitchen, dish.id, servings = 4)

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

            val result = cook(dish, pantry)(kitchen, dish.id, servings = 2)

            assertTrue(result is AppResult.Success)
            assertEquals(Quantity(300.0, unit), pantry.quantityOf("item-1"))
        }

    @Test
    fun `a line that asks for no amount consumes nothing`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1"))
            val pantry = pantryOf(someOfIngredientOne)

            val result = cook(dish, pantry)(kitchen, dish.id, servings = 2)

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
                    ConsumePantryItemsUseCase(pantry, TimeProvider { now }, noUnits()),
                    TimeProvider { now },
                    noUnits(),
                )

            assertTrue(useCase(kitchen, dish.id, servings = 2) is AppResult.Failure)
            assertTrue(useCase(kitchen, dish, servings = 2) is AppResult.Success)
            assertEquals(Quantity(300.0, unit), pantry.quantityOf("item-1"))
        }

    @Test
    fun `a failing pantry read is reported and never reads as an empty pantry`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(200.0, unit)))
            val pantry = FakePantryRepositoryContract(readError = AppError.Network())

            assertTrue(cook(dish, pantry)(kitchen, dish.id, servings = 2) is AppResult.Failure)
        }

    // Two servings, so that asking for four is a doubling and not the recipe as it stands.
    private fun dishOf(vararg lines: RecipeIngredient): Recipe = recipe(servings = 2, ingredients = lines.toList())

    private fun pantryOf(vararg held: PantryItem): FakePantryRepositoryContract =
        FakePantryRepositoryContract(held.toList())

    private fun cook(
        dish: Recipe,
        pantry: FakePantryRepositoryContract,
        units: GetUnitConverterUseCase = noUnits(),
    ): CookRecipeUseCase =
        CookRecipeUseCase(
            FakeRecipeRepositoryContract(catalogue = listOf(dish)),
            pantry,
            ConsumePantryItemsUseCase(pantry, TimeProvider { now }, units),
            TimeProvider { now },
            units,
        )

    private fun FakePantryRepositoryContract.quantityOf(id: String): Quantity =
        items.first { it.id == pantryItemId(id) }.quantity

    @Test
    fun `cooking consumes the right amount across units and leaves the holding in its own unit`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(0.2, kilogram)))
            val pantry = pantryOf(pantryItem("item-1", "ing-1", Quantity(500.0, gram)))

            val result = cook(dish, pantry, metricUnits("taxonomy-1"))(kitchen, dish.id, servings = 2)

            assertTrue(result is AppResult.Success)
            assertEquals(Quantity(300.0, gram), pantry.quantityOf("item-1"))
        }

    @Test
    fun `a line is split over holdings in different units`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(0.5, kilogram)))
            val pantry =
                pantryOf(
                    pantryItem("item-1", "ing-1", Quantity(300.0, gram)),
                    pantryItem("item-2", "ing-1", Quantity(1.0, kilogram)),
                )

            val result = cook(dish, pantry, metricUnits("taxonomy-1"))(kitchen, dish.id, servings = 2)

            assertTrue(result is AppResult.Success)
            // The first holding is emptied, the second only gives the 200 g still needed.
            assertTrue(pantry.items.none { it.id == pantryItemId("item-1") })
            assertEquals(Quantity(0.8, kilogram), pantry.quantityOf("item-2"))
        }

    @Test
    fun `a pantry that holds exactly what the recipe asks in another unit is emptied`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(4.03, kilogram)))
            val pantry = pantryOf(pantryItem("item-1", "ing-1", Quantity(4030.0, gram)))

            val result = cook(dish, pantry, metricUnits("taxonomy-1"))(kitchen, dish.id, servings = 2)

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(pantryItemId("item-1")), pantry.removed)
        }

    @Test
    fun `a holding too small across units stops the cook before anything is written`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(0.6, kilogram)))
            val pantry = pantryOf(pantryItem("item-1", "ing-1", Quantity(500.0, gram)))

            val result = cook(dish, pantry, metricUnits("taxonomy-1"))(kitchen, dish.id, servings = 2)

            assertTrue(result is AppResult.Failure)
            assertEquals(0, pantry.upsertAllCalls)
            assertEquals(Quantity(500.0, gram), pantry.quantityOf("item-1"))
        }

    @Test
    fun `a converter that cannot be read fails the cook and consumes nothing`() =
        runTest {
            val dish = dishOf(recipeIngredient("ing-1", quantity = Quantity(200.0, unit)))
            val pantry = pantryOf(someOfIngredientOne)
            val broken =
                GetUnitConverterUseCase(
                    FakeTaxonomyRepositoryContract(metricUnitTerms("taxonomy-1"), taxonomiesError = AppError.Network()),
                )

            val result = cook(dish, pantry, broken)(kitchen, dish.id, servings = 2)

            assertTrue(result is AppResult.Failure)
            assertEquals(0, pantry.upsertAllCalls)
            assertEquals(Quantity(500.0, unit), pantry.quantityOf("item-1"))
        }
}
