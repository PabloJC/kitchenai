package com.kitchenai.shared.domain.model

/** A constraint the user put on suggestions: an opaque [TermRef] plus how hard it binds. */
data class DietaryConstraint(
    val term: TermRef,
    val strength: ConstraintStrength,
)

/**
 * How hard a constraint binds is app logic — it drives filtering — not vocabulary, which is
 * why it may be an enum while the term it applies to may not.
 */
enum class ConstraintStrength {
    EXCLUDE,
    AVOID,
    PREFER,
}
