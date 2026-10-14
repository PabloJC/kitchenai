package com.kitchenai.ui.designsystem.format

/** Languages that write a decimal comma: "1,5 kg". Anything not listed writes a dot. */
private val CommaLanguages =
    setOf(
        "es", "fr", "de", "it", "pt", "nl", "ca", "eu", "gl", "ru", "uk", "pl", "cs", "sk", "sl", "hr",
        "sr", "bg", "ro", "hu", "el", "tr", "sv", "da", "nb", "nn", "no", "fi", "et", "lv", "lt", "id", "vi",
    )

/** Spanish-speaking regions that write a decimal dot, the exception to their language's rule. */
private val DotSpanishRegions = setOf("MX", "US", "PR", "DO", "GT", "HN", "NI", "PA", "SV")

/**
 * The decimal separator for the user's first preferred locale, derived from its language tag so
 * that `commonMain` needs no platform number formatter.
 */
fun decimalSeparatorFor(languageTags: List<String>): Char {
    val parts = languageTags.firstOrNull()?.split('-', '_').orEmpty()
    val language = parts.firstOrNull()?.lowercase() ?: return '.'
    val region = parts.drop(1).firstOrNull { part -> part.length == 2 }?.uppercase()
    return when {
        language == "es" && region in DotSpanishRegions -> '.'
        language in CommaLanguages -> ','
        else -> '.'
    }
}
