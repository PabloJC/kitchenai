package com.kitchenai.ui.designsystem.format

/**
 * Digits with at most one separator, which may be `.` or `,`. The pattern carries no sign:
 * a negative amount is malformed input, not a value to clamp.
 */
private val AmountPattern = Regex("""\d*[.,]?\d*""")

/**
 * Parses what a keyboard emits into an amount, or `null` when it is not one yet.
 *
 * Both decimal separators are accepted because the device locale, not the app, decides which
 * key the user gets: a field that only understands `1.5` is broken for most of the world.
 */
fun parseAmount(input: String): Double? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (!AmountPattern.matches(trimmed)) return null
    return trimmed.replace(',', '.').toDoubleOrNull()
}

/**
 * An amount beside its unit, with the trailing `.0` dropped: "2 pieces", never "2.0 pieces".
 * A unit the catalogue could not name is left out rather than shown as an identifier.
 */
fun formatQuantity(
    amount: Double,
    unitLabel: String?,
): String = listOfNotNull(amount.trimmed(), unitLabel).joinToString(" ")

/** A whole number keeps no fraction: nobody writes "3.0 onions". */
private fun Double.trimmed(): String = if (this == toLong().toDouble()) toLong().toString() else toString()
