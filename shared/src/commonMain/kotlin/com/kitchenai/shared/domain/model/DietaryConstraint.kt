package com.kitchenai.shared.domain.model

/** A constraint the user put on suggestions: an opaque [TermRef] plus how hard it binds. */
data class DietaryConstraint(
    val term: TermRef,
    val strength: ConstraintStrength,
)

/**
 * How hard a constraint binds is app logic — it drives filtering — not vocabulary, which is
 * why it may be an enum while the term it applies to may not.
 *
 * Two states, not three: [AVOID] hard-filters — it is what `EXCLUDE` used to be, renamed rather
 * than removed, so a term already excluded before this change keeps being excluded
 * (`UserProfileMapper.toStrength` maps a stored `"EXCLUDE"` onto this same value). There is no
 * softer "avoid" left between preferring and hard-filtering; that middle state was collapsed
 * into this one on purpose (#180).
 */
enum class ConstraintStrength {
    AVOID,
    PREFER,
    ;

    /**
     * The next binding a repeated choice lands on, and [SOFTEST] is where a term binds the first
     * time: hard-filtering a food is a decision the user makes deliberately, never one a single
     * tap makes for them.
     */
    fun next(): ConstraintStrength =
        when (this) {
            PREFER -> AVOID
            AVOID -> PREFER
        }

    companion object {
        val SOFTEST: ConstraintStrength = PREFER
    }
}
