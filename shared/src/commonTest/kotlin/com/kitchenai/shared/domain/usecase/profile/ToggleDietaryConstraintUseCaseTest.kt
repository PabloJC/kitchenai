package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ToggleDietaryConstraintUseCaseTest {
    private val toggle = ToggleDietaryConstraintUseCase()
    private val first = termRef("t1", "a")
    private val second = termRef("t1", "b")
    private val profile =
        UserProfile.newFor(
            (UserId.of("u1") as AppResult.Success).data,
            listOf("xx"),
            Instant.fromEpochSeconds(1),
        )

    @Test
    fun `an absent term is added with the strength asked for`() {
        val result = toggle(profile, first, ConstraintStrength.EXCLUDE)

        assertEquals(listOf(DietaryConstraint(first, ConstraintStrength.EXCLUDE)), result.constraints)
    }

    @Test
    fun `a present term is removed whatever strength is asked for`() {
        val withFirst = toggle(profile, first, ConstraintStrength.EXCLUDE)

        val result = toggle(withFirst, first, ConstraintStrength.PREFER)

        assertEquals(emptyList(), result.constraints)
    }

    @Test
    fun `toggling twice leaves the profile as it was`() {
        val result = toggle(toggle(profile, first, ConstraintStrength.AVOID), first, ConstraintStrength.AVOID)

        assertEquals(profile, result)
    }

    @Test
    fun `the other constraints keep the strength they had`() {
        val start = toggle(profile, second, ConstraintStrength.PREFER)

        val result = toggle(toggle(start, first, ConstraintStrength.EXCLUDE), first, ConstraintStrength.EXCLUDE)

        assertEquals(listOf(DietaryConstraint(second, ConstraintStrength.PREFER)), result.constraints)
    }
}

/** Opaque fixture shared by the tests of this package: no fixture ever names a real term. */
internal fun termRef(
    taxonomy: String,
    term: String,
): TermRef =
    TermRef(
        (TaxonomyId.of(taxonomy) as AppResult.Success).data,
        (TermId.of(term) as AppResult.Success).data,
    )
