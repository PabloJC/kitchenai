package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryMatch
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.FakePantryPort
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
                    FakePantryPort(),
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
                    FakePantryPort(readError = AppError.Network()),
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

    private fun matcher(pantry: List<PantryItem>): MatchRecipeAgainstPantry =
        MatchRecipeAgainstPantry(
            FakeRecipePort(catalogue = listOf(stored)),
            FakePantryPort(pantry),
            TimeProvider { now },
        )

    private fun AppResult<PantryMatch>.unwrap(): PantryMatch = (this as AppResult.Success).data
}
