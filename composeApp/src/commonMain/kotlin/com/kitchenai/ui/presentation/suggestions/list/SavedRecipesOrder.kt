package com.kitchenai.ui.presentation.suggestions.list

/** By title, ignoring case; blank finds everything. */
internal fun List<SuggestionUi>.searchedFor(query: String): List<SuggestionUi> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { recipe -> recipe.title.contains(needle, ignoreCase = true) }
}

/**
 * What the pantry covers today first. Stable, so each group keeps the listener's order, newest
 * save first.
 */
internal fun List<SuggestionUi>.cookableFirst(): List<SuggestionUi> = sortedBy { recipe -> recipe.missing.isNotEmpty() }
