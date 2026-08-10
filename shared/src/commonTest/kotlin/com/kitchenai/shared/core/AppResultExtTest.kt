package com.kitchenai.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AppResultExtTest {
    @Test
    fun `getOrElse devuelve el valor de un Success sin llamar al fallback`() {
        var llamadas = 0
        val result: AppResult<Int> = AppResult.Success(42)

        val valor =
            result.getOrElse {
                llamadas++
                0
            }

        assertEquals(42, valor)
        assertEquals(0, llamadas)
    }

    @Test
    fun `getOrElse aplica el fallback y recibe el error de un Failure`() {
        val error = AppError.NotFound("recipe")
        val result: AppResult<Int> = AppResult.Failure(error)

        val valor = result.getOrElse { if (it == error) -1 else -2 }

        assertEquals(-1, valor)
    }
}
