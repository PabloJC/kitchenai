package com.kitchenai.ui.designsystem.format

import kotlin.math.abs
import kotlin.math.roundToLong

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
 * An amount beside its unit: "2 pieces", "33,33 g". A unit the catalogue could not name is left
 * out rather than shown as an identifier.
 */
fun formatQuantity(
    amount: Double,
    unitLabel: String?,
    separator: Char = '.',
): String = listOfNotNull(formatAmount(amount, separator), unitLabel).joinToString(" ")

/**
 * At most two decimals with the trailing zeros dropped: scaling 100 g to one of three servings
 * reads "33.33", never "33.333333333333336", and nobody writes "3.0 onions".
 */
fun formatAmount(
    amount: Double,
    separator: Char = '.',
): String {
    val hundredths = (abs(amount) * HUNDREDTHS).roundToLong()
    val whole = hundredths / HUNDREDTHS
    val fraction = (hundredths % HUNDREDTHS).toString().padStart(2, '0').trimEnd('0')
    val sign = if (amount < 0 && hundredths != 0L) "-" else ""
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole$separator$fraction"
}

private const val HUNDREDTHS = 100L
