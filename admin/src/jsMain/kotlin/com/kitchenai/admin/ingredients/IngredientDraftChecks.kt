package com.kitchenai.admin.ingredients

// Same limits and messages as functions/src/catalogueEntry.ts, so the form refuses what the callable would.
private const val MAX_ID_LENGTH = 128
private const val MAX_LABEL = 200
private const val MAX_LANGUAGES = 20
private val ID_ALPHABET = Regex("^[A-Za-z0-9._:-]+$")
private val LANGUAGE_TAG = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$")

/** The first thing `writeCatalogue` would refuse in this draft, or null when it would accept it. */
fun IngredientDraft.problem(): String? = ingredientIdProblem(id) ?: labelsProblem(filledLabels())

/** Rows left blank are not sent: an empty language is one the ingredient has no name in. */
fun IngredientDraft.filledLabels(): List<LabelRow> =
    labels.filter { row -> row.text.isNotBlank() }.map { row -> row.copy(language = row.language.trim()) }

/** Also guards the picture upload, whose file name starts with the id. */
fun ingredientIdProblem(id: String): String? =
    when {
        id.isEmpty() || id.length > MAX_ID_LENGTH -> "id must be a short identifier"
        !ID_ALPHABET.matches(id) -> "id must not contain free text"
        id == "." || id == ".." || id.startsWith("__") -> "id is reserved"
        else -> null
    }

private fun labelsProblem(rows: List<LabelRow>): String? =
    when {
        rows.isEmpty() || rows.size > MAX_LANGUAGES -> "labels needs 1 to $MAX_LANGUAGES languages"
        rows.any { row -> !LANGUAGE_TAG.matches(row.language) } -> "label key must be a language tag"
        rows.any {
                row ->
            row.text.length > MAX_LABEL
        } -> "label must be non-empty text of at most $MAX_LABEL characters"
        // Admin-only: the callable receives a map, where a repeated language silently keeps one.
        rows.map { row -> row.language }.toSet().size < rows.size -> "each language can be named once"
        else -> null
    }
