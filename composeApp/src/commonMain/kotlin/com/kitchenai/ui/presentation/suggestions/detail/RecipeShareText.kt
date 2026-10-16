package com.kitchenai.ui.presentation.suggestions.detail

/**
 * The recipe as plain text, amounts at the servings on screen. Every line in one list: the
 * pantry buckets are this phone's business, not the reader's on the other end.
 */
fun RecipeDetailUiState.shareText(labels: ShareLabels): String =
    buildString {
        appendLine(title)
        summary?.let { appendLine(it) }
        appendLine(listOfNotNull(labels.minutes, labels.servings).joinToString(" · "))
        appendLine()
        appendLine(labels.ingredients)
        (held + missing + unverifiable).forEach { line ->
            val amount = line.quantity?.let { "$it " }.orEmpty()
            val optional = if (line.optional) labels.optionalSuffix else ""
            appendLine("- $amount${line.name}$optional")
        }
        if (steps.isNotEmpty()) {
            appendLine()
            appendLine(labels.steps)
            steps.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
        }
    }.trimEnd()
