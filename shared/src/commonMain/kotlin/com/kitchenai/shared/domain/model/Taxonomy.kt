package com.kitchenai.shared.domain.model

/**
 * A named vocabulary: the catalogue a [TermRef] points into.
 *
 * Code knows that *a* taxonomy exists because a document says so; it never knows what is
 * inside it. [labels] is keyed by BCP-47 language tag, and [defaultLanguageTag] is the last
 * link of the resolution chain when the reader speaks none of the languages on offer.
 */
data class Taxonomy(
    val id: TaxonomyId,
    val labels: Map<String, String>,
    val defaultLanguageTag: String? = null,
)
