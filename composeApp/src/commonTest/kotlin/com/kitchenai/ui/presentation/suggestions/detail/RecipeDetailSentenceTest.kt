package com.kitchenai.ui.presentation.suggestions.detail

import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.snack_added_count
import com.kitchenai.ui.resources.snack_counts_joined
import com.kitchenai.ui.resources.snack_not_needed_count
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeDetailSentenceTest {
    @Test
    fun `both counts reach the sentence in the order the reader sees them`() {
        val sentence = RecipeDetailEvent.AddedToList(added = 1, skipped = 5).sentence()

        // Each count picks its own plural form, so this pins the structure: the added count first
        // and the skipped one second. Swapping them would read as five added and one skipped,
        // which is the mistake worth catching. What each form says is checked against the real
        // resources in PluralSentenceTest.
        val expected =
            UiText.Joined(
                Res.string.snack_counts_joined,
                listOf(
                    UiText.Plural(Res.plurals.snack_added_count, 1),
                    UiText.Plural(Res.plurals.snack_not_needed_count, 5),
                ),
            )
        assertEquals(expected, sentence)
    }

    @Test
    fun `a refusal is carried through rather than wrapped`() {
        val refusal = UiText.Raw("You are missing ingredients for this")

        assertEquals(refusal, RecipeDetailEvent.Failed(refusal).sentence())
    }
}
