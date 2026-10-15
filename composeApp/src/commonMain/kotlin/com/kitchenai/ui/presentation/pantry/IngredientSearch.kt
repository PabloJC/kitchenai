package com.kitchenai.ui.presentation.pantry

/** Rows the add sheet renders: enough to find what you typed, few enough to need no scrolling. */
internal const val MAX_INGREDIENT_MATCHES = 6

/**
 * The options [query] finds, best first: names that start with it, then words that do, then the
 * rest. Case and accents are ignored, and an empty query finds nothing rather than everything.
 */
internal fun List<IngredientOption>.rankedMatches(
    query: String,
    limit: Int = MAX_INGREDIENT_MATCHES,
): List<IngredientOption> {
    val needle = foldForSearch(query).trim()
    if (needle.isEmpty()) return emptyList()
    return mapNotNull { option ->
        val folded = foldForSearch(option.label)
        matchTier(folded, needle)?.let { tier -> RankedOption(option, tier, folded) }
    }.sortedWith(compareBy<RankedOption>({ it.tier }, { it.folded.length }, { it.folded }))
        .take(limit)
        .map { ranked -> ranked.option }
}

/** Lowercase with accents removed, so "Limón" and "limon" compare equal. */
internal fun foldForSearch(text: String): String =
    buildString(text.length) {
        for (char in text.lowercase()) {
            // Combining marks come from keyboards that type the accent separately.
            if (char in COMBINING_MARKS) continue
            val base = FOLDED[char]
            if (base != null) append(base) else append(char)
        }
    }

private data class RankedOption(
    val option: IngredientOption,
    val tier: Int,
    val folded: String,
)

private fun matchTier(
    folded: String,
    needle: String,
): Int? {
    if (folded.startsWith(needle)) return TIER_PREFIX
    var from = 0
    var contained = false
    while (true) {
        val at = folded.indexOf(needle, from)
        if (at < 0) break
        if (at > 0 && !folded[at - 1].isLetterOrDigit()) return TIER_WORD_START
        contained = true
        from = at + 1
    }
    return if (contained) TIER_CONTAINS else null
}

private const val TIER_PREFIX = 0
private const val TIER_WORD_START = 1
private const val TIER_CONTAINS = 2

private val COMBINING_MARKS = '̀'..'ͯ'

// commonMain has no Normalizer, so the Latin letters a food name can carry are listed by hand.
private val FOLDED: Map<Char, String> =
    buildMap {
        val groups =
            mapOf(
                "a" to "àáâãäåāăą",
                "c" to "çćĉċč",
                "d" to "ďđ",
                "e" to "èéêëēĕėęě",
                "g" to "ĝğġģ",
                "h" to "ĥħ",
                "i" to "ìíîïĩīĭįı",
                "j" to "ĵ",
                "k" to "ķ",
                "l" to "ĺļľŀł",
                "n" to "ñńņň",
                "o" to "òóôõöøōŏő",
                "r" to "ŕŗř",
                "s" to "śŝşš",
                "t" to "ţťŧ",
                "u" to "ùúûüũūŭůűų",
                "w" to "ŵ",
                "y" to "ýÿŷ",
                "z" to "źżž",
                "ae" to "æ",
                "oe" to "œ",
                "ss" to "ß",
            )
        groups.forEach { (base, accented) -> accented.forEach { char -> put(char, base) } }
    }
