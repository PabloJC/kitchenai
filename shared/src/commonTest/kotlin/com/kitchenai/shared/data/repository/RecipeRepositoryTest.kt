package com.kitchenai.shared.data.repository

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.testDispatchers
import com.kitchenai.shared.data.local.FakeRecipeDao
import com.kitchenai.shared.data.local.RecipeLocalDataSource
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.recipe.recipe
import com.kitchenai.shared.domain.usecase.recipe.recipeIngredient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeRepositoryTest {
    private val localDataSource = RecipeLocalDataSource(FakeRecipeDao())
    private val repository =
        RecipeRepository(localDataSource, TimeProvider { SAVED_AT }, testDispatchers(UnconfinedTestDispatcher()))

    @Test
    fun `a recipe written on one launch is readable on the next`() =
        runTest {
            val original =
                recipe(ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(2.0, termRef("term-1")))))
                    .copy(steps = listOf("step-1"), tags = listOf(termRef("t-1")))

            repository.replaceAll(listOf(original))

            assertEquals(AppResult.Success(listOf(original)), repository.getAll())
        }

    @Test
    fun `a replaced generation leaves nothing behind`() =
        runTest {
            repository.replaceAll(listOf(recipe(id = "recipe-1")))

            repository.replaceAll(listOf(recipe(id = "recipe-2")))

            assertEquals(AppResult.Success(listOf(recipe(id = "recipe-2"))), repository.getAll())
        }

    private companion object {
        val SAVED_AT: Instant = Instant.fromEpochMilliseconds(1_000)
    }
}
