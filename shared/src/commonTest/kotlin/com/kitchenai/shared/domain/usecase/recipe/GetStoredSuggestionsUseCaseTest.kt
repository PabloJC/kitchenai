package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.FakePantryRepositoryContract
import com.kitchenai.shared.domain.usecase.pantry.pantryItem
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

class GetStoredSuggestionsUseCaseTest {
    private val now = Instant.fromEpochSeconds(1_000)
    private val unit = termRef("term-1")
    private val stored = recipe(ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(2.0, unit))))

    @Test
    fun `matches the stored generation against the pantry as it stands now`() =
        runTest {
            val useCase =
                GetStoredSuggestionsUseCase(
                    FakeRecipeRepositoryContract(stored = listOf(stored)),
                    FakePantryRepositoryContract(listOf(pantryItem("item-1", "ing-1", Quantity(2.0, unit)))),
                    TimeProvider { now },
                    noUnits(),
                )

            val result = (useCase(kitchen) as AppResult.Success).data

            assertEquals(1f, result.single().match.coverage)
            assertEquals(stored, result.single().recipe)
        }

    @Test
    fun `no generation stored is an empty list rather than an error`() =
        runTest {
            val useCase =
                GetStoredSuggestionsUseCase(
                    FakeRecipeRepositoryContract(),
                    FakePantryRepositoryContract(),
                    TimeProvider { now },
                    noUnits(),
                )

            assertEquals(AppResult.Success(emptyList()), useCase(kitchen))
        }

    @Test
    fun `a failing local read is reported`() =
        runTest {
            val useCase =
                GetStoredSuggestionsUseCase(
                    FakeRecipeRepositoryContract(readError = AppError.Unknown()),
                    FakePantryRepositoryContract(),
                    TimeProvider { now },
                    noUnits(),
                )

            assertTrue(useCase(kitchen) is AppResult.Failure)
        }

    @Test
    fun `a failing pantry read is reported`() =
        runTest {
            val useCase =
                GetStoredSuggestionsUseCase(
                    FakeRecipeRepositoryContract(stored = listOf(stored)),
                    FakePantryRepositoryContract(readError = AppError.Network()),
                    TimeProvider { now },
                    noUnits(),
                )

            assertTrue(useCase(kitchen) is AppResult.Failure)
        }

    @Test
    fun `stored recipes are matched across units when the units convert`() =
        runTest {
            val milk =
                recipe(
                    ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(250.0, termRef("millilitre")))),
                )
            val useCase =
                GetStoredSuggestionsUseCase(
                    FakeRecipeRepositoryContract(stored = listOf(milk)),
                    FakePantryRepositoryContract(
                        listOf(pantryItem("item-1", "ing-1", Quantity(1.0, termRef("litre")))),
                    ),
                    TimeProvider { now },
                    metricUnits("taxonomy-1"),
                )

            assertEquals(1f, (useCase(kitchen) as AppResult.Success).data.single().match.coverage)
        }

    @Test
    fun `a converter that cannot be read is reported`() =
        runTest {
            val broken =
                GetUnitConverterUseCase(
                    FakeTaxonomyRepositoryContract(metricUnitTerms("taxonomy-1"), termsError = AppError.Network()),
                )
            val useCase =
                GetStoredSuggestionsUseCase(
                    FakeRecipeRepositoryContract(stored = listOf(stored)),
                    FakePantryRepositoryContract(),
                    TimeProvider { now },
                    broken,
                )

            assertTrue(useCase(kitchen) is AppResult.Failure)
        }
}
