package com.kitchenai.shared.domain.agent

import com.kitchenai.shared.domain.model.Quantity

/**
 * A holding the catalogue has never heard of, as the agent sees it: a short, sanitised name and
 * nothing else the user wrote. The server re-checks [name] and treats it as data, never as instruction.
 */
data class PantryFreeTextEntry(
    val name: String,
    val quantity: Quantity,
    val expiring: Boolean,
)
