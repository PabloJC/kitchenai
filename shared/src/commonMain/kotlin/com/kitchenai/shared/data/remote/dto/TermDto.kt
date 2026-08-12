package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `taxonomies/{taxonomyId}/terms/{termId}` catalogue document, read-only for the client.
 *
 * [order] defaults to zero rather than to a sentinel: a document that forgot it sorts first,
 * which is visible, instead of sorting last, which looks deliberate.
 */
@Serializable
data class TermDto(
    val labels: Map<String, String> = emptyMap(),
    val parent: String? = null,
    val order: Int = 0,
)
