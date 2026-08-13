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
    ;

    /**
     * The next binding a repeated choice lands on. It wraps from the hardest back to the
     * softest, and [SOFTEST] is where a term binds the first time: excluding a food is a
     * decision the user makes deliberately, never one a single tap makes for them.
     */
    fun next(): ConstraintStrength =
        when (this) {
            PREFER -> AVOID
            AVOID -> EXCLUDE
            EXCLUDE -> PREFER
        }

    companion object {
        val SOFTEST: ConstraintStrength = PREFER
    }
}
