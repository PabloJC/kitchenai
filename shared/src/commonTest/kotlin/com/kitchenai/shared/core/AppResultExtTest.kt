package com.kitchenai.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AppResultExtTest {
    @Test
    fun `getOrElse returns the value of a Success without calling the fallback`() {
        var calls = 0
        val result: AppResult<Int> = AppResult.Success(42)

        val value =
            result.getOrElse {
                calls++
                0
            }

        assertEquals(42, value)
        assertEquals(0, calls)
    }

    @Test
    fun `getOrElse applies the fallback and receives the error of a Failure`() {
        val error = AppError.NotFound("recipe")
        val result: AppResult<Int> = AppResult.Failure(error)

        val value = result.getOrElse { if (it == error) -1 else -2 }

        assertEquals(-1, value)
    }
}
