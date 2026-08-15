package com.kitchenai.ui.presentation.suggestions

import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.snack_added_to_list
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeDetailSentenceTest {
    @Test
    fun `both counts reach the sentence in the order the reader sees them`() {
        val sentence = RecipeDetailEvent.AddedToList(added = 1, skipped = 5).sentence()

        // What the counts mean is now in the string table, where the translation lives, so this
        // pins the structure: the right key, and the two numbers the right way round. Swapping
        // them would read as five added and one skipped, which is the mistake worth catching.
        assertEquals(UiText.of(Res.string.snack_added_to_list, 1, 5), sentence)
    }

    @Test
    fun `a refusal is carried through rather than wrapped`() {
        val refusal = UiText.Raw("You are missing ingredients for this")

        assertEquals(refusal, RecipeDetailEvent.Failed(refusal).sentence())
    }
}
