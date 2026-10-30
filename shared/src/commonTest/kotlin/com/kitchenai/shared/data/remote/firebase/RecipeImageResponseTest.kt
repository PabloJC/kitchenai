package com.kitchenai.shared.data.remote.firebase

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.data.remote.dto.RecipeImageResponseDto
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeImageResponseTest {
    @Test
    fun `a web address is passed on`() {
        val url = "https://images.test/recipe.jpg"

        assertEquals(AppResult.Success(url), RecipeImageResponseDto(1, url).imageUrlOrError())
    }

    @Test
    fun `an unknown schema is refused`() {
        val result = RecipeImageResponseDto(2, "https://images.test/recipe.jpg").imageUrlOrError()

        assertEquals("schemaVersion", ((result as AppResult.Failure).error as AppError.Validation).field)
    }

    @Test
    fun `an address that is not a web address is refused`() {
        val result = RecipeImageResponseDto(1, "file:///etc/hosts").imageUrlOrError()

        assertEquals("imageUrl", ((result as AppResult.Failure).error as AppError.Validation).field)
    }
}
