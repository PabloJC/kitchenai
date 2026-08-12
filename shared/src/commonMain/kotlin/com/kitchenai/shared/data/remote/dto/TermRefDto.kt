package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A reference to one catalogue term, as its two identifiers. A list of these rather than a map
 * grouped by taxonomy: the domain type is a `List` and the user's order has to survive the
 * round trip, which grouping does not preserve when preferences interleave taxonomies.
 */
@Serializable
data class TermRefDto(
    val taxonomy: String? = null,
    val term: String? = null,
)
