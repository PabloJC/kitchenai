package com.kitchenai.shared.domain.model

/**
 * Resolves a label map against a preference chain: exact tag, then primary subtag, then the
 * catalogue's declared default.
 *
 * A miss returns null instead of a placeholder, which leaves the "missing translation"
 * decision to presentation.
 */
fun Map<String, String>.resolve(
    preferred: List<String>,
    defaultLanguageTag: String? = null,
): String? {
    // Each candidate is exhausted before moving to the next: the first preference wins even
    // when it only matches by primary subtag.
    val chain = preferred + listOfNotNull(defaultLanguageTag)
    return chain.firstNotNullOfOrNull { tag -> exactMatch(tag) ?: primarySubtagMatch(tag) }
}

/** BCP-47 tags are case-insensitive, and catalogues are written by hand. */
private fun Map<String, String>.exactMatch(tag: String): String? =
    entries.firstOrNull { entry -> entry.key.equals(tag, ignoreCase = true) }?.value

/** A reader asking for "xx-YY" is served by an "xx" label. */
private fun Map<String, String>.primarySubtagMatch(tag: String): String? {
    val primary = tag.substringBefore('-')
    return entries.firstOrNull { entry -> entry.key.substringBefore('-').equals(primary, ignoreCase = true) }?.value
}
