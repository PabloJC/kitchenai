package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserProfile

/**
 * Adds or removes the constraint on [term], leaving every other constraint untouched with
 * the strength it already had. Editing the list here keeps it out of the ViewModel.
 */
class ToggleDietaryConstraintUseCase {
    operator fun invoke(
        profile: UserProfile,
        term: TermRef,
        strength: ConstraintStrength,
    ): UserProfile {
        val current = profile.constraints
        val next =
            if (current.any { it.term == term }) {
                current.filterNot { it.term == term }
            } else {
                current + DietaryConstraint(term, strength)
            }
        return profile.copy(constraints = next)
    }
}
