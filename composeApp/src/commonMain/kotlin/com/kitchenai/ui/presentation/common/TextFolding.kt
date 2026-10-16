package com.kitchenai.ui.presentation.common

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
