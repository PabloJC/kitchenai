package com.kitchenai.shared.data.local

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.usecase.pantry.termRef
import com.kitchenai.shared.domain.usecase.recipe.recipe
import com.kitchenai.shared.domain.usecase.recipe.recipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The same round trip [com.kitchenai.shared.data.mapper.RecipeMapperTest] proves for Firestore,
 * through the entity instead.
 */
class RecipeEntityMapperTest {
    @Test
    fun `a recipe round trips through its entity`() {
        val original =
            recipe(ingredients = listOf(recipeIngredient("ing-1", quantity = Quantity(2.0, termRef("term-1")))))
                .copy(steps = listOf("step-1"), tags = listOf(termRef("t-1")))

        val restored = original.toEntity(SAVED_AT).toRecipe()

        assertEquals(AppResult.Success(original), restored)
    }

    private companion object {
        val SAVED_AT: Instant = Instant.fromEpochMilliseconds(1_000)
    }
}
