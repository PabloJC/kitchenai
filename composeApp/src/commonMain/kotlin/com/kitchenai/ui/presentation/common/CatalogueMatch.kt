package com.kitchenai.ui.presentation.common

/**
 * The one option whose label is [text], ignoring case, accents and spacing, or null. Two options
 * with that label are a tie and give null: guessing would save the wrong one as if it were picked.
 */
internal fun <T> exactCatalogueMatch(
    text: String,
    options: List<T>,
    label: (T) -> String,
): T? {
    val needle = normalised(text)
    if (needle.isEmpty()) return null
    return options.filter { option -> normalised(label(option)) == needle }.singleOrNull()
}

private val SPACES = Regex("\\s+")

private fun normalised(text: String): String = foldForSearch(text).trim().replace(SPACES, " ")
