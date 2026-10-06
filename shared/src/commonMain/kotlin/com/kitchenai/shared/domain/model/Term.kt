package com.kitchenai.shared.domain.model

/**
 * One entry of a taxonomy, and the only place a label ever lives.
 *
 * [parent] lets a catalogue express that a term belongs to a family without the code knowing
 * either name; [order] is the catalogue's own display order, so it does not depend on a
 * locale's collation.
 *
 * [conversion] is set only on a unit that converts into others; every other term, a piece
 * included, has none and stays an opaque identifier.
 */
data class Term(
    val ref: TermRef,
    val labels: Map<String, String>,
    val parent: TermId?,
    val order: Int,
    val conversion: UnitConversion? = null,
)
