package com.kitchenai.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppResultTest {
    @Test
    fun `map transforma el valor de un Success`() {
        val result: AppResult<Int> = AppResult.Success(21)
        val doubled = result.map { it * 2 }
        assertEquals(AppResult.Success(42), doubled)
    }

    @Test
    fun `map deja intacto un Failure`() {
        val error = AppError.NotFound("recipe")
        val result: AppResult<Int> = AppResult.Failure(error)
        val mapped = result.map { it * 2 }
        assertTrue(mapped is AppResult.Failure)
        assertEquals(error, mapped.error)
    }
}
