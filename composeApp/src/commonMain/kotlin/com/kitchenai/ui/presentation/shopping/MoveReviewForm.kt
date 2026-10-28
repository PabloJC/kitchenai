package com.kitchenai.ui.presentation.shopping

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitchenai.shared.domain.model.MoveToPantryOptions
import com.kitchenai.shared.domain.model.ShoppingItemId
import kotlin.time.Instant

/**
 * The review sheet's answers, keyed by line id: a line that leaves the cart while the sheet is
 * open simply drops out of [options].
 */
@Stable
internal class MoveReviewForm {
    private val dates = mutableStateMapOf<ShoppingItemId, Instant>()
    private val amounts = mutableStateMapOf<ShoppingItemId, Double>()
    private val malformed = mutableStateMapOf<ShoppingItemId, Boolean>()

    var sharedDate by mutableStateOf<Instant?>(null)
        private set

    /** The one line whose date shortcuts are open; one at a time keeps a weekly shop scrollable. */
    var openLine by mutableStateOf<ShoppingItemId?>(null)

    fun dateOf(id: ShoppingItemId): Instant? = dates[id]

    fun setDate(
        id: ShoppingItemId,
        date: Instant?,
    ) {
        if (date == null) dates.remove(id) else dates[id] = date
    }

    /** Overwrites every line, the ones already dated by hand too: that is what "for all" says. */
    fun setAll(
        date: Instant?,
        lines: List<ShoppingItemUi>,
    ) {
        sharedDate = date
        lines.forEach { line -> setDate(line.id, date) }
    }

    /** Null is a blank field, which moves as one. */
    fun setAmount(
        id: ShoppingItemId,
        amount: Double?,
    ) {
        if (amount == null) amounts.remove(id) else amounts[id] = amount
    }

    fun setMalformed(
        id: ShoppingItemId,
        notANumber: Boolean,
    ) {
        malformed[id] = notANumber
    }

    /** Zero or less is refused like text that is not a number: the pantry would refuse it too. */
    fun canMove(lines: List<ShoppingItemUi>): Boolean =
        lines.isNotEmpty() &&
            lines.none {
                    line ->
                malformed[line.id] == true || (amounts[line.id] ?: MoveToPantryOptions.BARE_AMOUNT) <= 0.0
            }

    /** Skip dates still moves the amounts typed here: only the dates are skipped. */
    fun options(
        lines: List<ShoppingItemUi>,
        withDates: Boolean,
    ): MoveToPantryOptions {
        val ids = lines.map { it.id }.toSet()
        return MoveToPantryOptions(
            expiresAt = if (withDates) dates.filterKeys { it in ids } else emptyMap(),
            defaultAmountForBare = true,
            bareAmounts = amounts.filterKeys { it in ids },
        )
    }
}
