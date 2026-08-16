package com.kitchenai.shared.domain.usecase.shopping

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AddedToListSummary
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Recipe
import com.kitchenai.shared.domain.model.RecipeIngredient
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.recipe.FakeRecipePort
import com.kitchenai.shared.domain.usecase.recipe.recipe
import com.kitchenai.shared.domain.usecase.recipe.recipeId
import com.kitchenai.shared.domain.usecase.recipe.recipeIngredient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddMissingIngredientsToShoppingListTest {
    private val user = userId()
    private val list = listId()
    private val unit = termRef("taxonomy-1", "term-a")
    private val items = FakeShoppingItemRepositoryContract()
    private val twoHundredOfIngredientOne = recipeIngredient("ing-1", quantity = Quantity(200.0, unit))

    @Test
    fun `a missing ingredient reaches the list carrying the recipe it came from`() =
        runTest {
            val dish = dishOf(twoHundredOfIngredientOne)

            val result = useCase(dish)(user, list, dish.id, servings = 2)

            val stored = items.itemsOf(list).single()
            assertEquals(ingredientId("ing-1"), stored.ingredient)
            assertEquals(dish.id, stored.sourceRecipe)
            assertEquals(Quantity(200.0, unit), stored.quantity)
            assertEquals(AddedToListSummary(added = 1, skipped = 0), result.unwrap())
        }

    @Test
    fun `an optional ingredient is left off the list`() =
        runTest {
            val optional = recipeIngredient("ing-2", quantity = Quantity(1.0, unit), optional = true)
            val dish = dishOf(twoHundredOfIngredientOne, optional)

            val result = useCase(dish)(user, list, dish.id, servings = 2)

            assertEquals(listOf(ingredientId("ing-1")), items.itemsOf(list).map { it.ingredient })
            assertEquals(AddedToListSummary(added = 1, skipped = 1), result.unwrap())
        }

    @Test
    fun `an unverifiable line is added as free text rather than assumed to be held`() =
        runTest {
            val dish = dishOf(recipeIngredient(freeText = "line-1"))

            useCase(dish)(user, list, dish.id, servings = 2)

            val stored = items.itemsOf(list).single()
            assertNull(stored.ingredient)
            assertEquals("line-1", stored.freeText)
        }

    @Test
    fun `every wanted line leaves in a single batched write`() =
        runTest {
            val second = recipeIngredient("ing-2", quantity = Quantity(1.0, unit))
            val dish = dishOf(twoHundredOfIngredientOne, second, recipeIngredient(freeText = "line-1"))

            useCase(dish)(user, list, dish.id, servings = 2)

            assertEquals(1, items.upsertCalls)
            assertEquals(3, items.itemsOf(list).size)
        }

    @Test
    fun `a recipe the pantry covers writes nothing and reports what it skipped`() =
        runTest {
            val dish = dishOf(twoHundredOfIngredientOne)
            val held = listOf(pantryItem("item-1", "ing-1", Quantity(500.0, unit)))

            val result = useCase(dish, held)(user, list, dish.id, servings = 2)

            assertEquals(AddedToListSummary(added = 0, skipped = 1), result.unwrap())
            assertEquals(0, items.upsertCalls)
        }

    @Test
    fun `what reaches the list is the shortfall for the servings asked for`() =
        runTest {
            val dish = dishOf(twoHundredOfIngredientOne)
            val held = listOf(pantryItem("item-1", "ing-1", Quantity(100.0, unit)))

            useCase(dish, held)(user, list, dish.id, servings = 4)

            assertEquals(Quantity(300.0, unit), items.itemsOf(list).single().quantity)
        }

    @Test
    fun `a line already on the list is topped up rather than duplicated`() =
        runTest {
            items.seed(list, shoppingItem("item-1", ingredient = "ing-1", quantity = Quantity(100.0, unit)))
            val dish = dishOf(twoHundredOfIngredientOne)

            useCase(dish)(user, list, dish.id, servings = 2)

            assertEquals(listOf(Quantity(300.0, unit)), items.itemsOf(list).map { it.quantity })
        }

    @Test
    fun `a failing recipe read is reported and nothing is written`() =
        runTest {
            val useCase =
                AddMissingIngredientsToShoppingList(
                    FakeRecipePort(readError = AppError.Unauthorized()),
                    FakePantryRepositoryContract(),
                    items,
                    sequentialIds(),
                    fixedTime(2_000),
                )

            assertTrue(useCase(user, list, recipeId("recipe-1"), servings = 2) is AppResult.Failure)
            assertEquals(0, items.upsertCalls)
        }

    @Test
    fun `a dish no repository has ever heard of reaches the list when the caller hands it over`() =
        runTest {
            val dish = dishOf(twoHundredOfIngredientOne)
            // An empty catalogue is a generated dish exactly: its id was minted on this device.
            val useCase =
                AddMissingIngredientsToShoppingList(
                    FakeRecipePort(),
                    FakePantryRepositoryContract(),
                    items,
                    sequentialIds(),
                    fixedTime(2_000),
                )

            assertTrue(useCase(user, list, dish.id, servings = 2) is AppResult.Failure)

            assertEquals(AddedToListSummary(added = 1, skipped = 0), useCase(user, list, dish, servings = 2).unwrap())
            assertEquals(ingredientId("ing-1"), items.itemsOf(list).single().ingredient)
        }

    // Two servings, so that asking for four is a doubling and not the recipe as it stands.
    private fun dishOf(vararg lines: RecipeIngredient): Recipe = recipe(servings = 2, ingredients = lines.toList())

    private fun useCase(
        dish: Recipe,
        held: List<PantryItem> = emptyList(),
    ): AddMissingIngredientsToShoppingList =
        AddMissingIngredientsToShoppingList(
            FakeRecipePort(catalogue = listOf(dish)),
            FakePantryRepositoryContract(held),
            items,
            sequentialIds(),
            fixedTime(2_000),
        )

    private fun AppResult<AddedToListSummary>.unwrap(): AddedToListSummary = (this as AppResult.Success).data
}
