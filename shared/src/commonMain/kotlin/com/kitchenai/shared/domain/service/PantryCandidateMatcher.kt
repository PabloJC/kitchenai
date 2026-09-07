package com.kitchenai.shared.domain.service

import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.RecipeIngredient

/**
 * Free-text pantry holdings that plausibly answer one of a match's unverifiable lines — offered
 * as a candidate for a person to confirm, never assumed. [PantryMatcher]'s own answer is
 * untouched by this: a candidate is a suggestion, not a second kind of coverage.
 *
 * Scoped to unverifiable lines with no catalogue id: a line [PantryMatcher] could not verify
 * only because of a unit mismatch already names its ingredient, and a free-text holding's own
 * words have nothing to add to that.
 */
object PantryCandidateMatcher {
    fun candidatesFor(
        unverifiable: List<RecipeIngredient>,
        pantry: List<PantryItem>,
    ): Map<RecipeIngredient, List<PantryItem>> {
        val holdings = pantry.filter { it.freeText != null }
        if (holdings.isEmpty()) return emptyMap()
        return unverifiable
            .mapNotNull { line -> line.freeText?.let { text -> line to text } }
            .associate { (line, text) -> line to holdings.filter { holding -> holding.plausiblyNames(text) } }
            .filterValues { it.isNotEmpty() }
    }

    // Substring either way, case- and edge-insensitive: enough to catch "bread" inside "the
    // good bread" without claiming any real understanding of either string.
    private fun PantryItem.plausiblyNames(text: String): Boolean {
        val holding = freeText?.trim()?.lowercase() ?: return false
        val wanted = text.trim().lowercase()
        return holding.isNotEmpty() && wanted.isNotEmpty() && (holding.contains(wanted) || wanted.contains(holding))
    }
}
