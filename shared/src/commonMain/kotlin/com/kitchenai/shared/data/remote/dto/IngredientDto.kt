package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `ingredients/{ingredientId}` catalogue document, read-only for the client.
 *
 * [labels] is keyed by language tag, never a bare display string: a catalogue entry is shared
 * across users and languages. [tags] is grouped by taxonomy so a tag query stays a single
 * `array-contains` on one field.
 */
@Serializable
data class IngredientDto(
    val labels: Map<String, String> = emptyMap(),
    val defaultUnitTaxonomy: String? = null,
    val defaultUnitTerm: String? = null,
    val tags: Map<String, List<String>> = emptyMap(),
)
