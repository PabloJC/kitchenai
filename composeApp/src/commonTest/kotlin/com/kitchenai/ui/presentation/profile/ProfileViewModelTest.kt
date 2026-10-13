package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TaxonomyPurpose
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.port.TaxonomyRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.ToggleDietaryConstraintUseCase
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithAppleUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SignOutUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToAppleAccountUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToGoogleAccountUseCase
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.PendingDisplayName
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
    private val sessions = FakeSessionPort(Session.SignedIn(userId, isAnonymous = true))
    private val pendingName = PendingDisplayName()

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
    fun `cycling toggles a selected term between the two strengths`() =
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
    fun `toggling writes nothing and one press writes once`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)

            viewModel.toggleConstraint(termRef("tx-1", "tm-1"))
            viewModel.cycleStrength(termRef("tx-1", "tm-1"))
            advanceUntilIdle()
            assertEquals(0, profiles.saveCount)

            viewModel.save()
            advanceUntilIdle()

            assertEquals(1, profiles.saveCount)
            assertEquals(ConstraintStrength.AVOID, profiles.saved?.constraints?.single()?.strength)
        }

    @Test
    fun `hasUnsavedChanges tracks an edit and clears once it is written`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)
            assertFalse(viewModel.state.value.hasUnsavedChanges)

            viewModel.toggleConstraint(termRef("tx-1", "tm-1"))
            advanceUntilIdle()
            assertTrue(viewModel.state.value.hasUnsavedChanges)

            viewModel.save()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.hasUnsavedChanges)
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
            assertEquals(UiText.of(Res.string.error_no_connection), sections.first().error)
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
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.error?.message)
        }

    /** Started and fed: the catalogue published, the profile delivered and every listener running. */
    @Test
    fun `a refused save is shown even while a listener banner is still up`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId)
            publish("t-1" to 1)
            profiles.profiles.emit(profile().copy(preferences = listOf(termRef("t-9", "a"))))
            advanceUntilIdle()

            // A listener failure raises a banner and nothing clears it on its own.
            profiles.errors.emit(AppError.Network())
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.generalError)

            viewModel.save()
            advanceUntilIdle()

            // The refusal is what the user just caused, so it is what they are told.
            assertEquals(UiText.Raw("references an unknown taxonomy"), viewModel.state.value.generalError)
        }

    @Test
    fun `a vocabulary the app reads structurally is not offered as a preference`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId)
            catalogue.publish(
                listOf(
                    taxonomy("t-1"),
                    Taxonomy(taxonomyId("t-2"), labels = mapOf("xx" to "structural"), purpose = TaxonomyPurpose.UNITS),
                ),
            )
            catalogue.termsOf(taxonomyId("t-1")).value = listOf(term("t-1", 1))
            catalogue.termsOf(taxonomyId("t-2")).value = listOf(term("t-2", 1))
            profiles.profiles.emit(profile())
            advanceUntilIdle()

            assertEquals(listOf(taxonomyId("t-1")), viewModel.state.value.sections.map { it.taxonomy })
        }

    @Test
    fun `an empty catalogue is answered but not failed`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId)
            profiles.profiles.emit(profile())
            catalogue.publish(emptyList())
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(true, state.isCatalogueLoaded)
            assertEquals(false, state.hasCatalogueFailed)
        }

    @Test
    fun `a catalogue that has not answered is not a catalogue that failed`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId)
            profiles.profiles.emit(profile())
            advanceUntilIdle()

            // The profile resolved first; the vocabulary is still on its way.
            assertEquals(false, viewModel.state.value.isCatalogueLoaded)

            publish("t-1" to 1)
            advanceUntilIdle()
            assertEquals(true, viewModel.state.value.isCatalogueLoaded)
        }

    @Test
    fun `a stream that recovers clears the banner it raised and no other`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)

            profiles.errors.emit(AppError.Network())
            advanceUntilIdle()
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.generalError)

            profiles.profiles.emit(profile())
            advanceUntilIdle()
            assertEquals(null, viewModel.state.value.generalError)
        }

    @Test
    fun `a guest who signs in is linked, keeps their uid and gets the name Google returned`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)

            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace")
            advanceUntilIdle()

            assertEquals(1, sessions.linkCalls)
            assertEquals(0, sessions.signInWithGoogleCalls)
            assertEquals(PendingDisplayName.Entry("Ada Lovelace", userId), pendingName.current.value)
            assertEquals(true, viewModel.state.value.hasAccount)
            assertEquals(false, viewModel.state.value.isConfirmingAccountSwitch)
        }

    @Test
    fun `a Google account that has its own kitchen asks before switching and shows no error`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            sessions.linkWithGoogleResult = AppResult.Failure(AppError.Conflict())

            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace")
            advanceUntilIdle()

            assertEquals(true, viewModel.state.value.isConfirmingAccountSwitch)
            assertEquals(false, viewModel.state.value.hasAccount)
            assertEquals(false, viewModel.state.value.isAuthenticating)
            assertNull(viewModel.state.value.generalError)
            assertNull(pendingName.current.value)
            assertEquals(0, sessions.signInWithGoogleCalls)
        }

    @Test
    fun `staying a guest closes the question and leaves the session alone`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            sessions.linkWithGoogleResult = AppResult.Failure(AppError.Conflict())
            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace")
            advanceUntilIdle()

            viewModel.cancelAccountSwitch()
            viewModel.confirmAccountSwitch()
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.isConfirmingAccountSwitch)
            assertEquals(0, sessions.signInWithGoogleCalls)
            assertEquals(Session.SignedIn(userId, isAnonymous = true), sessions.observeSession().first())
            assertNull(pendingName.current.value)
        }

    @Test
    fun `confirming the switch signs in with the same tokens and binds the name to the Google uid`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            val googleUserId = (UserId.of("user-google") as AppResult.Success).data
            sessions.linkWithGoogleResult = AppResult.Failure(AppError.Conflict())
            sessions.signInWithGoogleResult = AppResult.Success(Session.SignedIn(googleUserId, isAnonymous = false))
            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace", GoogleAccessToken("access-token"))
            advanceUntilIdle()

            viewModel.confirmAccountSwitch()
            advanceUntilIdle()

            assertEquals(1, sessions.signInWithGoogleCalls)
            assertEquals(GoogleAccessToken("access-token"), sessions.lastAccessToken)
            assertEquals(false, viewModel.state.value.isConfirmingAccountSwitch)
            assertEquals(PendingDisplayName.Entry("Ada Lovelace", googleUserId), pendingName.current.value)
        }

    @Test
    fun `a guest who signs in with Apple is linked and gets the name Apple returned`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)

            viewModel.signInWithApple(AppleIdToken("apple-token"), AppleRawNonce("nonce"), "Ada Lovelace")
            advanceUntilIdle()

            assertEquals(1, sessions.linkWithAppleCalls)
            assertEquals(AppleRawNonce("nonce"), sessions.lastAppleNonce)
            assertEquals(PendingDisplayName.Entry("Ada Lovelace", userId), pendingName.current.value)
            assertEquals(true, viewModel.state.value.hasAccount)
        }

    @Test
    fun `an Apple ID with its own kitchen asks, and confirming needs Apple's sheet again`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            sessions.linkWithAppleResult = AppResult.Failure(AppError.Conflict())

            viewModel.signInWithApple(AppleIdToken("apple-token"), AppleRawNonce("nonce"), null)
            advanceUntilIdle()
            // The consumed nonce cannot be replayed, so the plain confirm does nothing for Apple.
            viewModel.confirmAccountSwitch()
            advanceUntilIdle()

            assertEquals(true, viewModel.state.value.isConfirmingAccountSwitch)
            assertEquals(true, viewModel.state.value.switchNeedsAppleSheet)
            assertEquals(0, sessions.signInWithAppleCalls)
            assertNull(viewModel.state.value.generalError)
        }

    @Test
    fun `the confirmed Apple switch signs in with the fresh token and binds the name to that uid`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            val appleUserId = (UserId.of("user-apple") as AppResult.Success).data
            sessions.linkWithAppleResult = AppResult.Failure(AppError.Conflict())
            sessions.signInWithAppleResult = AppResult.Success(Session.SignedIn(appleUserId, isAnonymous = false))
            viewModel.signInWithApple(AppleIdToken("apple-token"), AppleRawNonce("nonce-1"), null)
            advanceUntilIdle()

            viewModel.switchToAppleAccount(AppleIdToken("apple-token-2"), AppleRawNonce("nonce-2"), "Ada Lovelace")
            advanceUntilIdle()

            assertEquals(1, sessions.signInWithAppleCalls)
            assertEquals(AppleRawNonce("nonce-2"), sessions.lastAppleNonce)
            assertEquals(false, viewModel.state.value.isConfirmingAccountSwitch)
            assertEquals(PendingDisplayName.Entry("Ada Lovelace", appleUserId), pendingName.current.value)
        }

    @Test
    fun `a Google conflict does not need Apple's sheet`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            sessions.linkWithGoogleResult = AppResult.Failure(AppError.Conflict())

            viewModel.signInWithGoogle(GoogleIdToken("id-token"), null)
            advanceUntilIdle()

            assertEquals(true, viewModel.state.value.isConfirmingAccountSwitch)
            assertEquals(false, viewModel.state.value.switchNeedsAppleSheet)
        }

    @Test
    fun `a switch that fails is shown and drops the name it offered`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            sessions.linkWithGoogleResult = AppResult.Failure(AppError.Conflict())
            sessions.signInWithGoogleResult = AppResult.Failure(AppError.Network())
            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace")
            advanceUntilIdle()

            viewModel.confirmAccountSwitch()
            advanceUntilIdle()

            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.generalError)
            assertNull(pendingName.current.value)
        }

    /** iOS cannot build the Firebase credential without it, so the sheet's access token must reach the port. */
    @Test
    fun `the access token the sheet returned reaches the sign-in`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)

            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace", GoogleAccessToken("access-token"))
            advanceUntilIdle()

            assertEquals(GoogleAccessToken("access-token"), sessions.lastAccessToken)
        }

    /** The name belongs to `SessionViewModel`, the only writer of `users/{uid}`: this screen never saves it. */
    @Test
    fun `a sign-in never writes the display name from this screen`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)

            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace")
            advanceUntilIdle()

            assertEquals(0, profiles.saveCount)
        }

    @Test
    fun `a blank display name offers nothing`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)

            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "   ")
            advanceUntilIdle()

            assertNull(pendingName.current.value)
        }

    @Test
    fun `a name offered before the sign-in answers is already waiting when the screen is torn down`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)

            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace")

            assertEquals(PendingDisplayName.Entry("Ada Lovelace"), pendingName.current.value)
            advanceUntilIdle()
        }

    /** The remote name wins over a draft that predates it: saving the draft must not erase the name. */
    @Test
    fun `a remote display name reaches an edited draft without discarding the edit`() =
        runTest(dispatcher) {
            val viewModel = ready("tx-1" to 1)
            viewModel.toggleConstraint(termRef("tx-1", "tm-1"))
            advanceUntilIdle()

            profiles.profiles.emit(profile().copy(displayName = "Ada Lovelace"))
            advanceUntilIdle()
            viewModel.save()
            advanceUntilIdle()

            assertEquals("Ada Lovelace", profiles.saved?.displayName)
            assertEquals(1, profiles.saved?.constraints?.size)
        }

    @Test
    fun `a failed sign-in leaves the previous state untouched`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            val before = viewModel.state.value
            sessions.linkWithGoogleResult = AppResult.Failure(AppError.Network())

            viewModel.signInWithGoogle(GoogleIdToken("id-token"), "Ada Lovelace")
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.hasAccount)
            assertEquals(before.displayName, viewModel.state.value.displayName)
            assertEquals(before.sections, viewModel.state.value.sections)
            assertEquals(0, profiles.saveCount)
            assertNull(pendingName.current.value)
            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.generalError)
        }

    @Test
    fun `signing out reaches the port and a failure is shown rather than swallowed`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            sessions.signOutResult = AppResult.Failure(AppError.Network())

            viewModel.signOut()
            advanceUntilIdle()

            assertEquals(UiText.of(Res.string.error_no_connection), viewModel.state.value.generalError)
        }

    @Test
    fun `a missing profile is shown as an error but never written from this screen`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(userId)

            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            // SessionViewModel owns creating users/{uid}; a second writer here could race it.
            assertEquals(0, profiles.saveCount)
        }

    @Test
    fun `after a sign-out the profile listener follows the uid the session hands over`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            val freshUserId = (UserId.of("fresh-anonymous") as AppResult.Success).data

            viewModel.signOut()
            advanceUntilIdle()
            // What SessionViewModel's re-established anonymous session looks like from here.
            sessions.emit(Session.SignedIn(freshUserId, isAnonymous = true))
            viewModel.start(freshUserId)
            advanceUntilIdle()

            assertEquals(freshUserId, profiles.observedUserIds.last())
            assertEquals(false, viewModel.state.value.hasAccount)
        }

    /** The raw session stream runs ahead of SessionViewModel's setup: only start() may move the listener. */
    @Test
    fun `a uid Firebase reports is not followed until the session hands it over`() =
        runTest(dispatcher) {
            val viewModel = ready("t-1" to 1)
            val googleUserId = (UserId.of("user-google") as AppResult.Success).data

            sessions.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            assertEquals(listOf(userId), profiles.observedUserIds)
            assertEquals(true, viewModel.state.value.hasAccount)
        }

    private suspend fun TestScope.ready(vararg sizes: Pair<String, Int>): ProfileViewModel {
        val viewModel = viewModel()
        viewModel.start(userId)
        publish(*sizes)
        profiles.profiles.emit(profile())
        advanceUntilIdle()
        return viewModel
    }

    private fun publish(vararg sizes: Pair<String, Int>) {
        catalogue.publish(sizes.map { (id, _) -> taxonomy(id) })
        sizes.forEach { (id, count) ->
            catalogue.termsOf(taxonomyId(id)).value = (1..count).map { index -> term(id, index) }
        }
    }

    @Test
    fun `a validation failure naming an input this screen does not bind still reaches the user`() =
        runTest(dispatcher) {
            // `preferences` is a field no input on this screen renders, so the message has
            // nowhere of its own to go and must fall through to the general one.
            val viewModel = viewModel()
            viewModel.start(userId)
            publish("t-1" to 1)
            profiles.profiles.emit(profile().copy(preferences = listOf(termRef("t-9", "a"))))
            advanceUntilIdle()

            viewModel.save()
            advanceUntilIdle()

            assertEquals(UiText.Raw("references an unknown taxonomy"), viewModel.state.value.generalError)
        }

    private fun viewModel(): ProfileViewModel =
        ProfileViewModel(
            observeUserProfile = ObserveUserProfileUseCase(profiles),
            observeTaxonomies = ObserveTaxonomiesUseCase(catalogue),
            observeTaxonomy = ObserveTaxonomyUseCase(catalogue),
            saveUserProfile =
                SaveUserProfileUseCase(
                    profiles,
                    catalogue,
                    FakeKitchenPort(),
                    TimeProvider { Instant.fromEpochSeconds(500) },
                ),
            toggleDietaryConstraint = ToggleDietaryConstraintUseCase(),
            accountDelegate =
                ProfileAccountDelegate(
                    observeSession = ObserveSessionUseCase(sessions),
                    signInWithGoogle = SignInWithGoogleUseCase(sessions),
                    switchToGoogleAccount = SwitchToGoogleAccountUseCase(sessions),
                    signInWithApple = SignInWithAppleUseCase(sessions),
                    switchToAppleAccount = SwitchToAppleAccountUseCase(sessions),
                    signOut = SignOutUseCase(sessions),
                    pendingDisplayName = pendingName,
                ),
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

private class FakeUserProfilePort : UserProfileRepositoryContract {
    val profiles = MutableSharedFlow<UserProfile>(replay = 1)
    val errors = MutableSharedFlow<AppError>()
    val observedUserIds = mutableListOf<UserId>()
    var saveCount = 0
        private set
    var saved: UserProfile? = null
        private set

    override fun observeProfile(userId: UserId): Flow<UserProfile> {
        observedUserIds += userId
        return profiles
    }

    override fun profileErrors(userId: UserId): Flow<AppError> = errors

    override suspend fun getProfile(userId: UserId): AppResult<UserProfile> =
        AppResult.Failure(
            AppError.NotFound("profile"),
        )

    override suspend fun save(profile: UserProfile): AppResult<Unit> {
        saveCount++
        saved = profile
        return AppResult.Success(Unit)
    }
}

/** Keyed exactly like the port it stands for, so one broken taxonomy can be told from all of them. */
private class FakeTaxonomyPort : TaxonomyRepositoryContract {
    // Replay without an initial value: a listener that has not answered emits nothing, which is
    // the state the screen has to tell apart from an empty catalogue.
    val taxonomies = MutableSharedFlow<List<Taxonomy>>(replay = 1)
    private var published: List<Taxonomy> = emptyList()
    private val terms = mutableMapOf<TaxonomyId, MutableStateFlow<List<Term>>>()
    private val errors = mutableMapOf<TaxonomyId, MutableSharedFlow<AppError>>()

    fun termsOf(id: TaxonomyId): MutableStateFlow<List<Term>> = terms.getOrPut(id) { MutableStateFlow(emptyList()) }

    fun errorsOf(id: TaxonomyId): MutableSharedFlow<AppError> = errors.getOrPut(id) { MutableSharedFlow() }

    override fun observeTaxonomy(id: TaxonomyId): Flow<List<Term>> = termsOf(id)

    override fun observeTaxonomies(): Flow<List<Taxonomy>> = taxonomies

    override fun taxonomyErrors(id: TaxonomyId): Flow<AppError> = errorsOf(id)

    override fun taxonomiesErrors(): Flow<AppError> = emptyFlow()

    fun publish(loaded: List<Taxonomy>) {
        published = loaded
        taxonomies.tryEmit(loaded)
    }

    override suspend fun getTaxonomies(): AppResult<List<Taxonomy>> = AppResult.Success(published)

    override suspend fun getTerms(id: TaxonomyId): AppResult<List<Term>> = AppResult.Success(termsOf(id).value)
}

/** Stateful on purpose: a sign-in or sign-out has to be visible on the same stream [observeSession] exposes. */
private class FakeSessionPort(
    initial: Session,
) : SessionRepositoryContract {
    private val state = MutableStateFlow(initial)

    var signInWithGoogleResult: AppResult<Session.SignedIn> =
        AppResult.Success(Session.SignedIn(userId, isAnonymous = false))
    var linkWithGoogleResult: AppResult<Session.SignedIn> =
        AppResult.Success(Session.SignedIn(userId, isAnonymous = false))
    var signOutResult: AppResult<Unit> = AppResult.Success(Unit)
    var lastAccessToken: GoogleAccessToken? = null
    var signInWithGoogleCalls = 0
    var linkCalls = 0
    var signInWithAppleResult: AppResult<Session.SignedIn> =
        AppResult.Success(Session.SignedIn(userId, isAnonymous = false))
    var linkWithAppleResult: AppResult<Session.SignedIn> =
        AppResult.Success(Session.SignedIn(userId, isAnonymous = false))
    var lastAppleNonce: AppleRawNonce? = null
    var signInWithAppleCalls = 0
    var linkWithAppleCalls = 0

    override fun observeSession(): Flow<Session> = state

    fun emit(session: Session) {
        state.value = session
    }

    override suspend fun signInAnonymously(): AppResult<Session.SignedIn> =
        AppResult.Success(Session.SignedIn(userId, isAnonymous = true))

    override suspend fun signInWithGoogle(
        idToken: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> {
        signInWithGoogleCalls++
        lastAccessToken = accessToken
        val result = signInWithGoogleResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun linkWithGoogle(
        idToken: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> {
        linkCalls++
        lastAccessToken = accessToken
        val result = linkWithGoogleResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun signInWithApple(
        idToken: AppleIdToken,
        rawNonce: AppleRawNonce,
    ): AppResult<Session.SignedIn> {
        signInWithAppleCalls++
        lastAppleNonce = rawNonce
        val result = signInWithAppleResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun linkWithApple(
        idToken: AppleIdToken,
        rawNonce: AppleRawNonce,
    ): AppResult<Session.SignedIn> {
        linkWithAppleCalls++
        lastAppleNonce = rawNonce
        val result = linkWithAppleResult
        if (result is AppResult.Success) state.value = result.data
        return result
    }

    override suspend fun signOut(): AppResult<Unit> {
        val result = signOutResult
        if (result is AppResult.Success) state.value = Session.SignedOut
        return result
    }
}
