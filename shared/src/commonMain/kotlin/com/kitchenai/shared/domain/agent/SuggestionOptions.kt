package com.kitchenai.shared.domain.agent

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * What the caller asks of a suggestion round, in numbers and flags only.
 *
 * [maxPantryEntries] and [expiringSoonWindow] are documented parameters rather than constants
 * buried in the builder: a cap that a caller cannot see is a bill and a truncated context
 * nobody chose.
 */
data class SuggestionOptions(
    val maxResults: Int = DEFAULT_MAX_RESULTS,
    val maxMinutes: Int? = null,
    /** True when the answer may only use what the pantry already holds. */
    val useOnlyPantry: Boolean = false,
    /** Upper bound on the holdings sent, most urgent first, so a large pantry cannot blow a context window. */
    val maxPantryEntries: Int = DEFAULT_MAX_PANTRY_ENTRIES,
    /** How much shelf life left still counts as urgent when ordering and flagging holdings. */
    val expiringSoonWindow: Duration = DEFAULT_EXPIRING_SOON_WINDOW,
) {
    companion object {
        const val DEFAULT_MAX_RESULTS: Int = 5
        const val DEFAULT_MAX_PANTRY_ENTRIES: Int = 40
        val DEFAULT_EXPIRING_SOON_WINDOW: Duration = 3.days
    }
}
