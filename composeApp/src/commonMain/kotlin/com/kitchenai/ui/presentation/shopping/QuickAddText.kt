package com.kitchenai.ui.presentation.shopping

import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.designsystem.format.parseAmount
import com.kitchenai.ui.presentation.common.foldForSearch

/** What one typed line says: "2 kg potatoes" is a name, an amount and a unit. */
data class QuickAddText(
    val name: String,
    val amount: Double? = null,
    val unit: TermRef? = null,
)

/**
 * Reads a leading amount, glued to its unit or not ("2 kg", "2kg", "1,5 l"), and a unit only
 * when one of [units]' spellings matches, singular or plural, ignoring case and accents. Text
 * that does not start with a number, or would leave no name, is all name.
 */
internal fun parseQuickAdd(
    text: String,
    units: List<Pair<TermRef, Set<String>>>,
): QuickAddText {
    val trimmed = text.trim()
    val match = LeadingAmount.find(trimmed) ?: return QuickAddText(trimmed)
    val amount = parseAmount(match.groupValues[1]) ?: return QuickAddText(trimmed)
    val rest = match.groupValues[2].trim()
    val unitWord = rest.substringBefore(' ')
    val unit = units.firstOrNull { (_, spellings) -> spellings.any { foldForSearch(it) == foldForSearch(unitWord) } }
    val name = if (unit != null) rest.substringAfter(' ', missingDelimiterValue = "").trim() else rest
    return when {
        name.isEmpty() -> QuickAddText(trimmed)
        else -> QuickAddText(name, amount, unit?.first)
    }
}

/** Digits with one optional separator, then the rest; the amount may touch the unit ("2kg"). */
private val LeadingAmount = Regex("""^(\d+(?:[.,]\d+)?)\s*(.*)$""")
