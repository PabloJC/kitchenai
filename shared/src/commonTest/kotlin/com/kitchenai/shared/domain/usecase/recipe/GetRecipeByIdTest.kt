package com.kitchenai.shared.domain.usecase.recipe

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetRecipeByIdTest {
    private val stored = recipe()

    @Test
    fun `reads a recipe nobody has saved`() =
        runTest {
            val result = GetRecipeById(FakeRecipePort(catalogue = listOf(stored)))(stored.id)

            assertEquals(AppResult.Success(stored), result)
        }

    @Test
    fun `an unknown id is not found`() =
        runTest {
            val result = GetRecipeById(FakeRecipePort())(stored.id)

            assertTrue(result is AppResult.Failure)
        }

    @Test
    fun `a failing read is reported`() =
        runTest {
            val result = GetRecipeById(FakeRecipePort(readError = AppError.Network()))(stored.id)

            assertTrue(result is AppResult.Failure)
        }
}
