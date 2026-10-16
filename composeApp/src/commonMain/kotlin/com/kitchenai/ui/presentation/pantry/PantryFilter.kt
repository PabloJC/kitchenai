package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.foldForSearch

/**
 * The rows a search and a location chip leave. The name matches anywhere in it, ignoring case
 * and accents like the add sheet does: "comino" finds "Comino molido", "limon" finds "Limón".
 */
internal fun List<PantryItemUi>.filteredBy(
    query: String,
    location: TermRef?,
): List<PantryItemUi> {
    val needle = foldForSearch(query).trim()
    return filter { item ->
        (location == null || item.location == location) &&
            (needle.isEmpty() || foldForSearch(item.name).contains(needle))
    }
}

/** Only the places the pantry actually uses, in the catalogue's order: a chip that empties the list helps nobody. */
internal fun List<Pair<TermRef, String>>.usedBy(items: List<PantryItemUi>): List<Pair<TermRef, String>> {
    val used = items.mapNotNullTo(mutableSetOf()) { item -> item.location }
    return filter { (ref, _) -> ref in used }
}
