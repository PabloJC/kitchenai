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
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.FakeKitchenRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.kitchen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class SaveUserProfileUseCaseTest {
    private val known = termRef("t1", "a")
    private val alsoKnown = termRef("t1", "b")
    private val unknown = termRef("t9", "a")
    private val savedAt = Instant.fromEpochSeconds(500)
    private val userId = (UserId.of("u1") as AppResult.Success).data
    private val profiles = RecordingProfilePort()
    private val kitchens = FakeKitchenRepositoryContract(kitchen(id = "kitchen-1", ownerId = userId))
    private val profile = UserProfile.newFor(userId, listOf("xx"), Instant.fromEpochSeconds(1))
    private val save =
        SaveUserProfileUseCase(
            profiles,
            FakeTaxonomyPort(AppResult.Success(listOf(Taxonomy(known.taxonomy, emptyMap())))),
            kitchens,
            TimeProvider { savedAt },
        )

    @Test
    fun `a valid profile is written with a fresh updatedAt`() =
        runTest {
            val result = save(profile.copy(constraints = listOf(avoid(known))))

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
            val result = save(profile.copy(constraints = listOf(avoid(known), avoid(known))))

            assertEquals("constraints", (result.errorOrNull() as AppError.Validation).field)
            assertNull(profiles.saved)
        }

    @Test
    fun `a constraint pointing at an unknown taxonomy is rejected`() =
        runTest {
            val result = save(profile.copy(constraints = listOf(avoid(unknown))))

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

            val result = SaveUserProfileUseCase(profiles, failing, kitchens, TimeProvider { savedAt })(profile)

            assertSame(error, result.errorOrNull())
            assertNull(profiles.saved)
        }

    @Test
    fun `a changed display name refreshes the caller's entry in the kitchen`() =
        runTest {
            profiles.existing = profile.copy(displayName = "Old Name")

            val result = save(profile.copy(displayName = "New Name"))

            assertEquals(AppResult.Success(Unit), result)
            assertEquals(listOf(Triple(userId, kitchens.current!!.id, "New Name")), kitchens.updatedDisplayNames)
        }

    @Test
    fun `an unchanged display name does not touch the kitchen`() =
        runTest {
            profiles.existing = profile.copy(displayName = "Same Name")

            save(profile.copy(displayName = "Same Name"))

            assertTrue(kitchens.updatedDisplayNames.isEmpty())
        }

    @Test
    fun `no display name at all does not touch the kitchen`() =
        runTest {
            profiles.existing = profile

            save(profile.copy(displayName = null))

            assertTrue(kitchens.updatedDisplayNames.isEmpty())
        }

    @Test
    fun `a kitchen sync failure does not fail an already-committed profile save`() =
        runTest {
            profiles.existing = profile.copy(displayName = "Old Name")
            kitchens.updateMyDisplayNameResult = AppResult.Failure(AppError.Network())

            val result = save(profile.copy(displayName = "New Name"))

            assertEquals(AppResult.Success(Unit), result)
            assertEquals("New Name", profiles.saved?.displayName)
        }

    @Test
    fun `no kitchen yet is not a failure`() =
        runTest {
            val noKitchen = FakeKitchenRepositoryContract()
            val useCase =
                SaveUserProfileUseCase(
                    profiles,
                    FakeTaxonomyPort(AppResult.Success(listOf(Taxonomy(known.taxonomy, emptyMap())))),
                    noKitchen,
                    TimeProvider { savedAt },
                )

            val result = useCase(profile.copy(displayName = "New Name"))

            assertEquals(AppResult.Success(Unit), result)
            assertTrue(noKitchen.updatedDisplayNames.isEmpty())
        }

    private fun avoid(term: TermRef) = DietaryConstraint(term, ConstraintStrength.AVOID)

    private fun AppResult<Unit>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
}

private class RecordingProfilePort : UserProfileRepositoryContract {
    var saved: UserProfile? = null
        private set

    /** What the listener reports before [save] overwrites it, so a "changed name" test has a baseline. */
    var existing: UserProfile? = null

    override fun observeProfile(userId: UserId): Flow<UserProfile> = existing?.let { flowOf(it) } ?: emptyFlow()

    override fun profileErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun save(profile: UserProfile): AppResult<Unit> {
        saved = profile
        return AppResult.Success(Unit)
    }
}

private class FakeTaxonomyPort(
    private val catalogue: AppResult<List<Taxonomy>>,
) : TaxonomyRepositoryContract {
    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> = flowOf(emptyList())

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = flowOf((catalogue as? AppResult.Success)?.data.orEmpty())

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = emptyFlow()

    override fun taxonomiesErrors(): Flow<AppError> = emptyFlow()

    // The one-shot read is what SaveUserProfileUseCase validates against, so this is where the
    // catalogue failure has to surface.
    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> = catalogue
}
