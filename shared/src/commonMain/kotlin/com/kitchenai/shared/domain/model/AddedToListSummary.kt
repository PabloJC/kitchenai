package com.kitchenai.shared.domain.model

/**
 * What pushing a recipe onto a shopping list actually did.
 *
 * A bare `Unit` would leave the caller guessing between "nothing was missing" and "nothing
 * happened", which are the same screen with two different messages.
 */
data class AddedToListSummary(
    val added: Int,
    val skipped: Int,
)
