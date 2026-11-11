package com.kitchenai.admin.requests

import com.kitchenai.admin.ingredients.IngredientDraft
import com.kitchenai.admin.ingredients.LabelRow
import com.kitchenai.admin.ingredients.NEW_LABEL_LANGUAGES

private const val MAX_SLUG = 64
private val NOT_SLUG = Regex("[^a-z0-9]+")

/** "Tomate cherry" → `tomate-cherry`: a suggestion the admin is expected to correct, often into English. */
fun slugFor(text: String): String = comparable(text).replace(NOT_SLUG, "-").take(MAX_SLUG).trim('-')

/** A new ingredient named after the request, with the text in [language] and the other seed languages blank. */
fun IngredientRequest.toIngredientDraft(language: String): IngredientDraft {
    val others = (NEW_LABEL_LANGUAGES - language).map { other -> LabelRow(other, "") }
    return IngredientDraft(id = slugFor(text), labels = listOf(LabelRow(language, text)) + others)
}
