package com.kitchenai.ui.presentation.shopping

import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.shopping_cleared_count
import com.kitchenai.ui.resources.snack_counts_joined
import com.kitchenai.ui.resources.snack_left_on_list_count
import com.kitchenai.ui.resources.snack_moved_count
import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingSentenceTest {
    @Test
    fun `moved and left counts each pick their own plural, moved first`() {
        val sentence = ShoppingEvent.MovedToPantry(moved = 1, skipped = 4).sentence()

        val expected =
            UiText.Joined(
                Res.string.snack_counts_joined,
                listOf(
                    UiText.Plural(Res.plurals.snack_moved_count, 1),
                    UiText.Plural(Res.plurals.snack_left_on_list_count, 4),
                ),
            )
        assertEquals(expected, sentence)
    }

    @Test
    fun `the cleared count is the quantity the plural is chosen by`() {
        assertEquals(
            UiText.Plural(Res.plurals.shopping_cleared_count, 2),
            ShoppingEvent.CheckedCleared(2).sentence(),
        )
    }
}
