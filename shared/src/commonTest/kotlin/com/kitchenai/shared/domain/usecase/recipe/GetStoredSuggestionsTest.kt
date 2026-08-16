package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
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

class GetStoredSuggestionsTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val unit = termRef("term-1")
    private val stored = recipe(ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(2.0, unit))))

    @Test
    fun `matches the stored generation against the pantry as it stands now`() =
        runTest {
            val useCase =
                GetStoredSuggestions(
                    FakeRecipeRepositoryContract(stored = listOf(stored)),
                    FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(2.0, unit)))),
                    TimeProvider { now },
                )

            val result = (useCase(user) as AppResult.Success).data

            assertEquals(1f, result.single().match.coverage)
            assertEquals(stored, result.single().recipe)
        }

    @Test
    fun `no generation stored is an empty list rather than an error`() =
        runTest {
            val useCase =
                GetStoredSuggestions(
                    FakeRecipeRepositoryContract(),
                    FakePantryRepositoryContract(),
                    TimeProvider { now },
                )

            assertEquals(AppResult.Success(emptyList()), useCase(user))
        }

    @Test
    fun `a failing local read is reported`() =
        runTest {
            val useCase =
                GetStoredSuggestions(
                    FakeRecipeRepositoryContract(readError = AppError.Unknown()),
                    FakePantryRepositoryContract(),
                    TimeProvider { now },
                )

            assertTrue(useCase(user) is AppResult.Failure)
        }

    @Test
    fun `a failing pantry read is reported`() =
        runTest {
            val useCase =
                GetStoredSuggestions(
                    FakeRecipeRepositoryContract(stored = listOf(stored)),
                    FakePantryRepositoryContract(readError = AppError.Network()),
                    TimeProvider { now },
                )

            assertTrue(useCase(user) is AppResult.Failure)
        }
}
