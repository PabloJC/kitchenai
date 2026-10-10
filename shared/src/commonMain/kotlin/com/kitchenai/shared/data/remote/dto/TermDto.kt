package com.kitchenai.shared.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The `taxonomies/{taxonomyId}/terms/{termId}` catalogue document, read-only for the client.
 *
 * [order] defaults to zero rather than to a sentinel: a document that forgot it sorts first,
 * which is visible, instead of sorting last, which looks deliberate.
 *
 * [conversion] is present only on a unit that converts into others; [pluralLabels] only on a
 * countable one, keyed by language like [labels].
 */
@Serializable
data class TermDto(
    val labels: Map<String, String> = emptyMap(),
    val parent: String? = null,
    val order: Int = 0,
    val conversion: UnitConversionDto? = null,
    val pluralLabels: Map<String, String> = emptyMap(),
)

/** The `conversion` map of a unit term: its dimension by name, and its factor to that dimension's base unit. */
@Serializable
data class UnitConversionDto(
    val dimension: String,
    val factor: Double,
)
