package com.kitchenai.ui.presentation.suggestions.cooking

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val NUMBER = """(\d+(?:[.,]\d+)?)"""

// A range keeps its lower bound only: checking a pan early costs less than burning it.
private const val RANGE = """(?:\s*(?:-|–|to|or|a|o|y)\s*\d+(?:[.,]\d+)?)?"""
private const val UNIT = """(hours?|hrs?|horas?|h|minutes?|mins?|minutos?|seconds?|secs?|segundos?|seg)"""

// No letter may follow the unit, so "2 hojas" is not two hours and "5 minced" is not five minutes.
private const val END = """(?![a-zA-ZáéíóúñÁÉÍÓÚÑ])"""

private val span = Regex("""$NUMBER$RANGE[\s-]*$UNIT$END""", RegexOption.IGNORE_CASE)

// Folds "1 hour and 30 minutes" and "1 h 30 min"; "1 hora y media" stays an hour, having no number to read.
private val joiner = Regex("""^\s*(?:,|and|y)?\s*""", RegexOption.IGNORE_CASE)

/**
 * The first span of time a step names, with a follow-on smaller unit folded in: "simmer for
 * 1 h 30 min" is ninety minutes, not a one-hour timer and a second one. Null when there is none.
 */
fun firstDurationIn(step: String): Duration? {
    val first = span.find(step) ?: return null
    var total = first.toDuration()
    var rest = step.substring(first.range.last + 1)
    var last = first
    while (true) {
        val lead = joiner.find(rest)?.value.orEmpty()
        val next = span.find(rest.substring(lead.length))?.takeIf { it.range.first == 0 } ?: break
        if (next.unitRank() >= last.unitRank()) break
        total += next.toDuration()
        rest = rest.substring(lead.length + next.range.last + 1)
        last = next
    }
    return total.takeIf { it > Duration.ZERO }
}

private fun MatchResult.toDuration(): Duration {
    val amount = groupValues[1].replace(',', '.').toDouble()
    return when (unitRank()) {
        HOURS -> amount.hours
        MINUTES -> amount.minutes
        else -> amount.seconds
    }
}

/** Only a smaller unit folds into the one before it: "10 minutes, then 5 minutes" is two steps of work. */
private fun MatchResult.unitRank(): Int =
    when (groupValues[2].lowercase().first()) {
        'h' -> HOURS
        'm' -> MINUTES
        else -> SECONDS
    }

private const val HOURS = 2
private const val MINUTES = 1
private const val SECONDS = 0
