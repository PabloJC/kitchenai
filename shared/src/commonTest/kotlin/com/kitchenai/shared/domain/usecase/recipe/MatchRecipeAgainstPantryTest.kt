package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
import com.kitchenai.shared.domain.usecase.pantry.termRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MatchRecipeAgainstPantryTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val unit = termRef("term-1")
    private val stored =
        recipe(ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(2.0, unit))))

    @Test
    fun `matches the stored recipe against the pantry as it stands now`() =
        runTest {
            val useCase = matcher(pantry = listOf(pantryItem("item-1", "ing-1", Quantity(2.0, unit))))

            val result = useCase(user, stored.id)

            assertEquals(1f, result.unwrap().coverage)
            assertEquals(stored.id, result.unwrap().recipeId)
        }

    @Test
    fun `a holding that expired before now does not count`() =
        runTest {
            val expired = pantryItem("item-1", "ing-1", Quantity(2.0, unit), expiresAt = Instant.fromEpochSeconds(1))
            val useCase = matcher(pantry = listOf(expired))

            assertEquals(0f, useCase(user, stored.id).unwrap().coverage)
        }

    @Test
    fun `a failing recipe read is reported and never becomes an empty match`() =
        runTest {
            val useCase =
                MatchRecipeAgainstPantry(
                    FakeRecipePort(readError = AppError.Unauthorized()),
                    FakePantryRepositoryContract(),
                    TimeProvider { now },
                )

            assertTrue(useCase(user, stored.id) is AppResult.Failure)
        }

    @Test
    fun `a failing pantry read is reported`() =
        runTest {
            val useCase =
                MatchRecipeAgainstPantry(
                    FakeRecipePort(catalogue = listOf(stored)),
                    FakePantryRepositoryContract(readError = AppError.Network()),
                    TimeProvider { now },
                )

            assertTrue(useCase(user, stored.id) is AppResult.Failure)
        }

    @Test
    fun `an unknown recipe is not found`() =
        runTest {
            val result = matcher(pantry = emptyList())(user, recipeId("recipe-2"))

            assertTrue(result is AppResult.Failure)
        }

    @Test
    fun `a doubled serving count needs more than the recipe's own`() =
        runTest {
            // Exactly enough for the recipe as written, and so not enough for twice it.
            val useCase = matcher(pantry = listOf(pantryItem("item-1", "ing-1", Quantity(2.0, unit))))

            assertEquals(1f, useCase(user, stored.id).unwrap().coverage)
            assertEquals(0f, useCase(user, stored.id, servings = stored.servings * 2).unwrap().coverage)
        }

    @Test
    fun `an override the recipe cannot scale to fails without reading the pantry`() =
        runTest {
            val pantry = FakePantryRepositoryContract(readError = AppError.Network())
            val recipes = FakeRecipePort(catalogue = listOf(stored))
            val useCase = MatchRecipeAgainstPantry(recipes, pantry, TimeProvider { now })

            // Network is what the pantry would answer; a validation failure proves it was never asked.
            val error = (useCase(user, stored.id, servings = 0) as AppResult.Failure).error

            assertTrue(error is AppError.Validation)
        }

    @Test
    fun `a dish no repository has ever heard of matches when the caller hands it over`() =
        runTest {
            // An empty catalogue is a generated dish exactly: its id was minted on this device.
            val useCase =
                MatchRecipeAgainstPantry(
                    FakeRecipePort(),
                    FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(2.0, unit)))),
                    TimeProvider { now },
                )

            assertTrue(useCase(user, stored.id) is AppResult.Failure)

            assertEquals(1f, useCase(user, stored).unwrap().coverage)
            // The override still applies to a recipe handed over, or the stepper could not move it.
            assertEquals(0f, useCase(user, stored, servings = stored.servings * 2).unwrap().coverage)
        }

    private fun matcher(pantry: List<PantryItem>): MatchRecipeAgainstPantry =
        MatchRecipeAgainstPantry(
            FakeRecipePort(catalogue = listOf(stored)),
            FakePantryRepositoryContract(pantry),
            TimeProvider { now },
        )

    private fun AppResult<PantryMatch>.unwrap(): PantryMatch = (this as AppResult.Success).data
}
