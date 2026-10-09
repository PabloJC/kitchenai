package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_cook_missing_hint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeDetailCookHintTest {
    private val line = IngredientLineUi(name = "Rice", quantity = null, optional = false)

    private fun loaded(
        missing: List<IngredientLineUi> = emptyList(),
        isWorking: Boolean = false,
    ) = RecipeDetailUiState(title = "Paella", isLoading = false, isWorking = isWorking, missing = missing)

    @Test
    fun `a missing ingredient is the reason Cook this is disabled and the hint counts them`() {
        val state = loaded(missing = listOf(line, line))

        assertEquals(false, state.canCook)
        assertEquals(UiText.Plural(Res.plurals.detail_cook_missing_hint, 2), state.cookHint())
    }

    @Test
    fun `nothing is said when the recipe can be cooked`() {
        val state = loaded()

        assertEquals(true, state.canCook)
        assertNull(state.cookHint())
    }

    @Test
    fun `a busy screen is disabled for a moment and says nothing about it`() {
        assertNull(loaded(missing = listOf(line), isWorking = true).cookHint())
    }

    @Test
    fun `a screen still loading says nothing`() {
        assertNull(RecipeDetailUiState(missing = listOf(line)).cookHint())
    }
}
