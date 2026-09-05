package com.kitchenai.shared.domain.model

/**
 * What moving the checked lines into the pantry actually did.
 *
 * A bare `Unit` would leave the caller guessing between "nothing was ticked" and "nothing had
 * an amount to move", which are the same screen with two different messages.
 */
data class MovedToPantrySummary(
    val moved: Int,
    /** Checked lines with no stated amount: a normal shopping line, not a normal pantry row. */
    val skipped: Int,
)
