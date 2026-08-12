package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `taxonomies/{taxonomyId}` catalogue document, read-only for the client.
 *
 * [labels] is keyed by language tag, never a bare display string: the catalogue is shared
 * across users and languages.
 */
@Serializable
data class TaxonomyDto(
    val labels: Map<String, String> = emptyMap(),
    val defaultLanguageTag: String? = null,
)
