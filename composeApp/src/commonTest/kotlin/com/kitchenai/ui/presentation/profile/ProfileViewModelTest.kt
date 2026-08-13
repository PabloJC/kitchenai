package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.TaxonomyPort
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfilePort
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomies
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomy
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfile
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfile
import com.kitchenai.shared.domain.usecase.profile.ToggleDietaryConstraint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = FakeUserProfilePort()
    private val catalogue = FakeTaxonomyPort()

    // `viewModelScope` runs on Dispatchers.Main, absent outside an app.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `one section is built for every taxonomy the catalogue holds`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 2, "tx-2" to 1)

            val sections = viewModel.state.value.sections
            assertEquals(listOf("tx-1", "tx-2"), sections.map { section -> section.taxonomy.value })
            assertEquals(listOf("taxonomy-tx-1", "taxonomy-tx-2"), sections.map { section -> section.title })
            assertEquals(listOf(2, 1), sections.map { section -> section.terms.size })
        }

    @Test
    fun `a term resolves to the label its catalogue entry carries`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)

            assertEquals("label-tm-1", viewModel.state.value.sections.single().terms.single().label)
        }

    @Test
    fun `an empty catalogue leaves no section rather than a list of its own`() =
        runTest(dispatcher) {
            val viewModel = ready()

            assertEquals(emptyList(), viewModel.state.value.sections)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `a term selected for the first time binds at the softest strength`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)

            viewModel.toggleConstraint(termRef("tx-1", "tm-1"))
            advanceUntilIdle()

            val chip = viewModel.state.value.sections.single().terms.single()
            assertTrue(chip.selected)
            assertEquals(ConstraintStrength.PREFER, chip.strength)
        }

    @Test
    fun `selecting a term twice leaves it unselected`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)

            viewModel.toggleConstraint(termRef("tx-1", "tm-1"))
            viewModel.toggleConstraint(termRef("tx-1", "tm-1"))
            advanceUntilIdle()

            assertNull(viewModel.state.value.sections.single().terms.single().strength)
        }

    @Test
    fun `cycling walks a selected term through every strength and back`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)
            val term = termRef("tx-1", "tm-1")

            fun strength(): ConstraintStrength? {
                advanceUntilIdle()
                return viewModel.state.value.sections.single().terms.single().strength
            }

            viewModel.toggleConstraint(term)
            assertEquals(ConstraintStrength.PREFER, strength())
            viewModel.cycleStrength(term)
            assertEquals(ConstraintStrength.AVOID, strength())
            viewModel.cycleStrength(term)
            assertEquals(ConstraintStrength.EXCLUDE, strength())
            viewModel.cycleStrength(term)
            assertEquals(ConstraintStrength.PREFER, strength())
        }

    @Test
    fun `a selection reaches the write with the strength it was given`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)

            viewModel.toggleConstraint(termRef("tx-1", "tm-1"))
            viewModel.cycleStrength(termRef("tx-1", "tm-1"))
            viewModel.save()
            advanceUntilIdle()

            val saved = profiles.saved?.constraints.orEmpty().single()
            assertEquals(termRef("tx-1", "tm-1"), saved.term)
            assertEquals(ConstraintStrength.AVOID, saved.strength)
        }

    @Test
    fun `a validation failure is reported against the field the use case named`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)

            viewModel.setServings(0)
            viewModel.save()
            advanceUntilIdle()

            assertEquals(SERVINGS_FIELD, viewModel.state.value.error?.field)
            assertEquals("must be at least 1", viewModel.state.value.errorFor(SERVINGS_FIELD))
            assertNull(viewModel.state.value.generalError)
            assertEquals(0, profiles.saveCount)
        }

    @Test
    fun `typing writes nothing and one press writes once`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)

            viewModel.setDisplayName("a")
            viewModel.setDisplayName("ab")
            viewModel.setServings(3)
            advanceUntilIdle()
            assertEquals(0, profiles.saveCount)

            viewModel.save()
            advanceUntilIdle()

            assertEquals(1, profiles.saveCount)
            assertEquals("ab", profiles.saved?.displayName)
            assertEquals(3, profiles.saved?.household?.servings)
        }

    @Test
    fun `a second press while the first is in flight writes once`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)

            viewModel.save()
            viewModel.save()
            advanceUntilIdle()

            assertEquals(1, profiles.saveCount)
        }

    @Test
    fun `a broken taxonomy listener costs its own section and no other`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 2, "tx-2" to 1)

            catalogue.errorsOf(taxonomyId("tx-1")).emit(AppError.Network())
            advanceUntilIdle()

            val sections = viewModel.state.value.sections
            assertEquals("No connection", sections.first().error)
            assertNull(sections.last().error)
            assertEquals(1, sections.last().terms.size)
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `a profile that never loads is a state the screen can draw`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId)
            advanceUntilIdle()

            profiles.errors.emit(AppError.Network())
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isLoading)
            assertEquals("No connection", viewModel.state.value.error?.message)
        }

    @Test
    fun `the transparency row counts what the profile actually carries`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 2)

            viewModel.toggleConstraint(termRef("tx-1", "tm-1"))
            viewModel.toggleConstraint(termRef("tx-1", "tm-2"))
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.constraintCount)
            assertEquals(listOf("xx"), viewModel.state.value.languageTags)
        }

    /** Started and fed: the catalogue published, the profile delivered and every listener running. */
    private suspend fun TestScope.ready(vararg sizes: Pair<String, Int>): ProfileViewModel {
        val viewModel = viewModel()
        viewModel.start(userId)
        publish(*sizes)
        profiles.profiles.emit(profile())
        advanceUntilIdle()
        return viewModel
    }

    private fun publish(vararg sizes: Pair<String, Int>) {
        catalogue.taxonomies.value = sizes.map { (id, _) -> taxonomy(id) }
        sizes.forEach { (id, count) ->
            catalogue.termsOf(taxonomyId(id)).value = (1..count).map { index -> term(id, index) }
        }
    }

    private fun viewModel(): ProfileViewModel =
        ProfileViewModel(
            observeUserProfile = ObserveUserProfile(profiles),
            observeTaxonomies = ObserveTaxonomies(catalogue),
            observeTaxonomy = ObserveTaxonomy(catalogue),
            saveUserProfile = SaveUserProfile(profiles, catalogue, TimeProvider { Instant.fromEpochSeconds(500) }),
            toggleDietaryConstraint = ToggleDietaryConstraint(),
            dispatchers = TestDispatcherProvider(dispatcher),
        )

    private fun profile(): UserProfile = UserProfile.newFor(userId, listOf("xx"), Instant.fromEpochSeconds(1))

    private fun taxonomy(id: String): Taxonomy = Taxonomy(taxonomyId(id), labels = mapOf("xx" to "taxonomy-$id"))

    private fun term(
        taxonomy: String,
        index: Int,
    ): Term =
        Term(
            ref = termRef(taxonomy, "tm-$index"),
            labels = mapOf("xx" to "label-tm-$index"),
            parent = null,
            order = index,
        )
}

// Opaque identifiers on purpose: a fixture that named a diet or an allergen would be the
// contextual constant this screen exists to keep out of the code.
private val userId = unwrap(UserId.of("user-1"))

private fun taxonomyId(raw: String): TaxonomyId = unwrap(TaxonomyId.of(raw))

private fun termRef(
    taxonomy: String,
    term: String,
): TermRef = TermRef(taxonomyId(taxonomy), unwrap(TermId.of(term)))

private fun <T> unwrap(result: AppResult<T>): T = (result as AppResult.Success).data

private class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}

private class FakeUserProfilePort : UserProfilePort {
    val profiles = MutableSharedFlow<UserProfile>(replay = 1)
    val errors = MutableSharedFlow<AppError>()
    var saveCount = 0
        private set
    var saved: UserProfile? = null
        private set

    override fun observeProfile(userId: UserId): Flow<UserProfile> = profiles

    override fun profileErrors(userId: UserId): Flow<AppError> = errors

    override suspend fun save(profile: UserProfile): AppResult<Unit> {
        saveCount++
        saved = profile
        return AppResult.Success(Unit)
    }
}

/** Keyed exactly like the port it stands for, so one broken taxonomy can be told from all of them. */
private class FakeTaxonomyPort : TaxonomyPort {
    val taxonomies = MutableStateFlow<List<Taxonomy>>(emptyList())
    private val terms = mutableMapOf<TaxonomyId, MutableStateFlow<List<Term>>>()
    private val errors = mutableMapOf<TaxonomyId, MutableSharedFlow<AppError>>()

    fun termsOf(id: TaxonomyId): MutableStateFlow<List<Term>> = terms.getOrPut(id) { MutableStateFlow(emptyList()) }

    fun errorsOf(id: TaxonomyId): MutableSharedFlow<AppError> = errors.getOrPut(id) { MutableSharedFlow() }

    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> = termsOf(id)

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = taxonomies

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = errorsOf(id)

    override fun taxonomiesErrors(): Flow<AppError> = emptyFlow()

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> = AppResult.Success(taxonomies.value)
}
