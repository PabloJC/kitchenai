package com.kitchenai.shared.domain.usecase.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.DietaryConstraint
import com.kitchenai.shared.domain.model.HouseholdContext
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.TaxonomyPort
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfilePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

class SaveUserProfileTest {
    private val known = termRef("t1", "a")
    private val alsoKnown = termRef("t1", "b")
    private val unknown = termRef("t9", "a")
    private val savedAt = Instant.fromEpochSeconds(500)
    private val profiles = RecordingProfilePort()
    private val profile =
        UserProfile.newFor(
            (UserId.of("u1") as AppResult.Success).data,
            listOf("xx"),
            Instant.fromEpochSeconds(1),
        )
    private val save =
        SaveUserProfile(
            profiles,
            FakeTaxonomyPort(AppResult.Success(listOf(Taxonomy(known.taxonomy, emptyMap())))),
            TimeProvider { savedAt },
        )

    @Test
    fun `a valid profile is written with a fresh updatedAt`() =
        runTest {
            val result = save(profile.copy(constraints = listOf(exclude(known))))

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(savedAt, profiles.saved?.updatedAt)
        }

    @Test
    fun `fewer than one serving is rejected naming the field`() =
        runTest {
            val result = save(profile.copy(household = HouseholdContext(servings = 0)))

            assertEquals(AppError.Validation("household.servings", "must be at least 1"), result.errorOrNull())
            assertNull(profiles.saved)
        }

    @Test
    fun `the same constraint term twice is rejected`() =
        runTest {
            val result = save(profile.copy(constraints = listOf(exclude(known), exclude(known))))

            assertEquals("constraints", (result.errorOrNull() as AppError.Validation).field)
            assertNull(profiles.saved)
        }

    @Test
    fun `a constraint pointing at an unknown taxonomy is rejected`() =
        runTest {
            val result = save(profile.copy(constraints = listOf(exclude(unknown))))

            assertEquals("constraints", (result.errorOrNull() as AppError.Validation).field)
            assertNull(profiles.saved)
        }

    @Test
    fun `a preference pointing at an unknown taxonomy is rejected`() =
        runTest {
            val result = save(profile.copy(preferences = listOf(alsoKnown, unknown)))

            assertEquals("preferences", (result.errorOrNull() as AppError.Validation).field)
            assertNull(profiles.saved)
        }

    @Test
    fun `a catalogue failure is propagated as it is`() =
        runTest {
            val error = AppError.Network()
            val failing = FakeTaxonomyPort(AppResult.Failure(error))

            val result = SaveUserProfile(profiles, failing, TimeProvider { savedAt })(profile)

            assertSame(error, result.errorOrNull())
            assertNull(profiles.saved)
        }

    private fun exclude(term: TermRef) = DietaryConstraint(term, ConstraintStrength.EXCLUDE)

    private fun AppResult<Unit>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
}

private class RecordingProfilePort : UserProfilePort {
    var saved: UserProfile? = null
        private set

    override fun observeProfile(userId: UserId): Flow<AppResult<UserProfile>> = flowOf()

    override suspend fun save(profile: UserProfile): AppResult<Unit> {
        saved = profile
        return AppResult.Success(Unit)
    }
}

private class FakeTaxonomyPort(
    private val catalogue: AppResult<List<Taxonomy>>,
) : TaxonomyPort {
    override fun observeTaxonomy(id: TaxonomyId): Flow<AppResult<List<Term>>> = flowOf(AppResult.Success(emptyList()))

    override fun observeTaxonomies(): Flow<AppResult<List<Taxonomy>>> = flowOf(catalogue)
}
