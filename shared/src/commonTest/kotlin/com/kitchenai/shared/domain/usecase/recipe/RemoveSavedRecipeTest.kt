package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class RemoveSavedRecipeTest {
    private val stored = recipe()

    @Test
    fun `drops the recipe from the saved ones`() =
        runTest {
            val port = FakeRecipeRepositoryContract(listOf(stored))

            RemoveSavedRecipe(port)(user, stored.id)

            assertTrue(port.recipes.isEmpty())
        }

    @Test
    fun `removing a recipe that is not there is not an error`() =
        runTest {
            val port = FakeRecipeRepositoryContract()

            assertTrue(RemoveSavedRecipe(port)(user, stored.id) is AppResult.Success)
        }

    @Test
    fun `a failing write is reported`() =
        runTest {
            val port = FakeRecipeRepositoryContract(listOf(stored), writeError = AppError.Network())

            assertTrue(RemoveSavedRecipe(port)(user, stored.id) is AppResult.Failure)
        }
}
