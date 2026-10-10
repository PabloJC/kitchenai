package com.kitchenai.ui.presentation.shopping

import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.shopping_cleared_count
import com.kitchenai.ui.resources.shopping_nothing_moved_no_amount
import com.kitchenai.ui.resources.snack_counts_joined
import com.kitchenai.ui.resources.snack_left_no_amount_count
import com.kitchenai.ui.resources.snack_moved_count
import kotlin.test.Test
import kotlin.test.assertEquals

class ShoppingSentenceTest {
    @Test
    fun `moved and left counts each pick their own plural with moved first`() {
        val sentence = ShoppingEvent.MovedToPantry(moved = 1, skipped = 4).sentence()

        val expected =
            UiText.Joined(
                Res.string.snack_counts_joined,
                listOf(
                    UiText.Plural(Res.plurals.snack_moved_count, 1),
                    UiText.Plural(Res.plurals.snack_left_no_amount_count, 4),
                ),
            )
        assertEquals(expected, sentence)
    }

    @Test
    fun `nothing moved because no line has an amount says so instead of counting zero`() {
        assertEquals(
            UiText.Plural(Res.plurals.shopping_nothing_moved_no_amount, 3),
            ShoppingEvent.MovedToPantry(moved = 0, skipped = 3).sentence(),
        )
    }

    @Test
    fun `a move that leaves nothing behind does not mention the list`() {
        assertEquals(
            UiText.Plural(Res.plurals.snack_moved_count, 2),
            ShoppingEvent.MovedToPantry(moved = 2, skipped = 0).sentence(),
        )
    }

    @Test
    fun `the cleared count is the quantity the plural is chosen by`() {
        assertEquals(
            UiText.Plural(Res.plurals.shopping_cleared_count, 2),
            ShoppingEvent.CheckedCleared(2).sentence(),
        )
    }
}
