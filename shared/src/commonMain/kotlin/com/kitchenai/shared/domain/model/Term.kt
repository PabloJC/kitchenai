package com.kitchenai.shared.domain.model

/**
 * One entry of a taxonomy, and the only place a label ever lives.
 *
 * [parent] lets a catalogue express that a term belongs to a family without the code knowing
 * either name; [order] is the catalogue's own display order, so it does not depend on a
 * locale's collation.
 */
data class Term(
    val ref: TermRef,
    val labels: Map<String, String>,
    val parent: TermId?,
    val order: Int,
)
