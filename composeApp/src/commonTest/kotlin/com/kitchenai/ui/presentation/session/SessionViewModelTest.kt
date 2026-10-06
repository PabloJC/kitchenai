package com.kitchenai.ui.presentation.session

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingListRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.kitchen.EnsureKitchenUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.session.EnsureSessionUseCase
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakeTaxonomyPort
import com.kitchenai.ui.presentation.common.PendingDisplayName
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_unauthorized_own_data
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionPort()
    private var kitchens = FakeKitchenPort()
    private val lists = FakeShoppingListPort()
    private val profiles = FakeUserProfilePort()
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
    fun `nothing is Ready until the session resolves`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.state.test {
                assertEquals(SessionUiState.Loading, awaitItem())
                viewModel.start(listOf("aa"), "list")
                assertEquals(SessionUiState.Ready(userId), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed sign in is retried into a Ready session`() =
        runTest(dispatcher) {
            sessions.signIn = AppResult.Failure(AppError.Network())
            val viewModel = viewModel()

            viewModel.state.test {
                assertEquals(SessionUiState.Loading, awaitItem())
                viewModel.start(listOf("aa"), "list")
                assertEquals(SessionUiState.Failed(UiText.of(Res.string.error_no_connection)), awaitItem())

                sessions.signIn = AppResult.Success(Session.SignedIn(userId, isAnonymous = true))
                viewModel.retry()
                assertEquals(SessionUiState.Loading, awaitItem())
                assertEquals(SessionUiState.Ready(userId), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed shopping list write ends in Failed`() =
        runTest(dispatcher) {
            lists.upsert = AppResult.Failure(AppError.Unauthorized())
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            assertEquals(SessionUiState.Failed(UNAUTHORIZED_MESSAGE), viewModel.state.value)
        }

    /** A kitchen that fails to resolve must not reach Ready (#194). */
    @Test
    fun `a failed kitchen ensure ends in Failed before the shopping list is ever touched`() =
        runTest(dispatcher) {
            kitchens = FakeKitchenPort(initial = null, readError = AppError.Network())
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            assertEquals(SessionUiState.Failed(UiText.of(Res.string.error_no_connection)), viewModel.state.value)
            assertEquals(0, lists.upsertCount)
        }

    @Test
    fun `the bootstrap runs once however often the gate is composed`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            assertEquals(1, sessions.signInCount)
            assertEquals(1, lists.upsertCount)
        }

    @Test
    fun `a missing profile is created once however often the listener reports it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            profiles.errors.emit(AppError.NotFound("profile"))
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            assertEquals(1, profiles.saveCount())
            assertEquals(listOf("aa"), profiles.saved?.languageTags)
        }

    @Test
    fun `a profile that already arrived on the data stream is never recreated`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            profiles.profiles.emit(UserProfile.newFor(userId, listOf("aa"), Instant.fromEpochSeconds(0)))
            advanceUntilIdle()
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            assertEquals(0, profiles.saveCount())
        }

    @Test
    fun `a listener failure that is not a missing document writes nothing and keeps the shell up`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            profiles.errors.emit(AppError.Network())
            advanceUntilIdle()

            assertEquals(0, profiles.saveCount())
            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
        }

    @Test
    fun `a failed profile write surfaces as a failure the user can retry`() =
        runTest(dispatcher) {
            profiles.saveResult = AppResult.Failure(AppError.Unauthorized())
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            assertEquals(SessionUiState.Failed(UNAUTHORIZED_MESSAGE), viewModel.state.value)
        }

    /** The first attempt's listener is still subscribed, so it and the bootstrap both reach `createProfile`. */
    @Test
    fun `a retry after Ready writes the profile once more and no further`() =
        runTest(dispatcher) {
            profiles.saveResult = AppResult.Failure(AppError.Unauthorized())
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()
            assertEquals(SessionUiState.Failed(UNAUTHORIZED_MESSAGE), viewModel.state.value)

            // Both writers are live from here: retry re-enters the bootstrap while the error
            // collector of the first attempt is still subscribed.
            profiles.saveResult = AppResult.Success(Unit)
            viewModel.retry()
            advanceUntilIdle()
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            // One failed write, then one that succeeded. A third would mean the guard let go.
            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
            assertEquals(2, profiles.saveCount())
        }

    /** A Google sign-in swaps the Firebase uid; `Ready` must follow it or every read keeps using the old one. */
    @Test
    fun `a Google sign-in swap while Ready moves the session to the new uid`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)

            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(googleUserId), viewModel.state.value)
            // The new uid's own kitchen/shopping-list setup ran too, not just the state label.
            assertEquals(2, lists.upsertCount)
        }

    /** Signing out clears the Firebase user outright (no re-link back to a restored anonymous one). */
    @Test
    fun `signing out while Ready re-establishes a fresh anonymous session instead of freezing`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            val freshAnonymousId = (UserId.of("user-2") as AppResult.Success).data
            sessions.signIn = AppResult.Success(Session.SignedIn(freshAnonymousId, isAnonymous = true))
            sessions.sessionChanges.emit(Session.SignedOut)
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(freshAnonymousId), viewModel.state.value)
            assertEquals(2, sessions.signInCount)
        }

    /** A session emission for the uid already active (a token refresh) must not re-run the setup. */
    @Test
    fun `a session emission for the same uid already active is a no-op`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            sessions.sessionChanges.emit(Session.SignedIn(userId, isAnonymous = true))
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
            assertEquals(1, lists.upsertCount)
        }

    /** A failing setup after `Ready` is not the cold-start `Failed`: the user has just signed in. */
    @Test
    fun `a kitchen failure after Ready is a switch failure that never reaches Ready for the new uid`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            kitchens.readError = AppError.Network()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            assertEquals(SessionUiState.SwitchFailed(UiText.of(Res.string.error_no_connection)), viewModel.state.value)
            // No listener under a uid whose setup never completed, and the old one is gone too.
            assertEquals(listOf(userId), profiles.observedUserIds)
        }

    @Test
    fun `a shopping list failure after Ready is a switch failure and the new uid is not Ready`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            lists.upsert = AppResult.Failure(AppError.Unauthorized())
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            assertEquals(SessionUiState.SwitchFailed(UNAUTHORIZED_MESSAGE), viewModel.state.value)
            assertEquals(listOf(userId), profiles.observedUserIds)
        }

    @Test
    fun `retrying a switch failure sets up the new uid only and never signs in again`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            kitchens.readError = AppError.Network()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            kitchens.readError = null
            viewModel.retry()
            // A second tap while the first is in flight must not start a second setup.
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(googleUserId), viewModel.state.value)
            assertEquals(1, sessions.signInCount)
            assertEquals(listOf(userId, googleUserId), profiles.observedUserIds)
            assertEquals(2, lists.upsertCount)
        }

    @Test
    fun `a switch retry that fails again stays recoverable`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            lists.upsert = AppResult.Failure(AppError.Unauthorized())
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            viewModel.retry()
            advanceUntilIdle()
            assertEquals(SessionUiState.SwitchFailed(UNAUTHORIZED_MESSAGE), viewModel.state.value)

            lists.upsert = AppResult.Success(Unit)
            viewModel.retry()
            advanceUntilIdle()
            assertEquals(SessionUiState.Ready(googleUserId), viewModel.state.value)
        }

    @Test
    fun `a stale profile error from the dropped uid neither writes nor replaces the switch failure`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            kitchens.readError = AppError.Network()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            assertEquals(0, profiles.saveCount())
            assertEquals(SessionUiState.SwitchFailed(UiText.of(Res.string.error_no_connection)), viewModel.state.value)
        }

    @Test
    fun `a failed re-established session after sign-out is recovered by retry`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            sessions.signIn = AppResult.Failure(AppError.Network())
            sessions.sessionChanges.emit(Session.SignedOut)
            advanceUntilIdle()
            assertEquals(SessionUiState.SwitchFailed(UiText.of(Res.string.error_no_connection)), viewModel.state.value)

            val freshAnonymousId = (UserId.of("user-2") as AppResult.Success).data
            sessions.signIn = AppResult.Success(Session.SignedIn(freshAnonymousId, isAnonymous = true))
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(freshAnonymousId), viewModel.state.value)
        }

    @Test
    fun `a name pending through a failed switch is applied to the existing profile once the retry succeeds`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            pendingName.expect("Ada Lovelace")
            kitchens.readError = AppError.Network()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()
            assertEquals(SessionUiState.SwitchFailed(UiText.of(Res.string.error_no_connection)), viewModel.state.value)

            kitchens.readError = null
            viewModel.retry()
            advanceUntilIdle()
            profiles.profiles.emit(UserProfile.newFor(googleUserId, listOf("aa"), Instant.fromEpochSeconds(0)))
            advanceUntilIdle()

            assertEquals("Ada Lovelace", profiles.saved?.displayName)
            assertEquals(googleUserId, profiles.saved?.userId)
            assertEquals(1, profiles.saveCount())
            assertNull(pendingName.current.value)
        }

    @Test
    fun `a name pending through a failed switch is written with the profile that retry creates`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            pendingName.expect("Ada Lovelace")
            kitchens.readError = AppError.Network()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            kitchens.readError = null
            viewModel.retry()
            advanceUntilIdle()
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            // One write, not a bare profile followed by a rename.
            assertEquals(1, profiles.saveCount())
            assertEquals("Ada Lovelace", profiles.saved?.displayName)
            assertNull(pendingName.current.value)
        }

    @Test
    fun `a name offered after the uid is already Ready is applied when the sign-in binds it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()
            profiles.profiles.emit(UserProfile.newFor(googleUserId, listOf("aa"), Instant.fromEpochSeconds(0)))
            advanceUntilIdle()

            pendingName.expect("Ada Lovelace")
            pendingName.bind(googleUserId)
            advanceUntilIdle()

            assertEquals("Ada Lovelace", profiles.saved?.displayName)
            assertNull(pendingName.current.value)
        }

    @Test
    fun `a name whose sign-in has not named its uid yet is never written to the current profile`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            pendingName.expect("Ada Lovelace")
            profiles.profiles.emit(UserProfile.newFor(userId, listOf("aa"), Instant.fromEpochSeconds(0)))
            advanceUntilIdle()

            assertEquals(0, profiles.saveCount())
            assertEquals(PendingDisplayName.Entry("Ada Lovelace"), pendingName.current.value)
        }

    @Test
    fun `a name bound to a uid is dropped when another uid becomes active`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            pendingName.expect("Ada Lovelace")
            pendingName.bind(googleUserId)

            val freshAnonymousId = (UserId.of("user-2") as AppResult.Success).data
            sessions.signIn = AppResult.Success(Session.SignedIn(freshAnonymousId, isAnonymous = true))
            sessions.sessionChanges.emit(Session.SignedOut)
            advanceUntilIdle()
            profiles.profiles.emit(UserProfile.newFor(freshAnonymousId, listOf("aa"), Instant.fromEpochSeconds(0)))
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(freshAnonymousId), viewModel.state.value)
            assertEquals(0, profiles.saveCount())
            assertNull(pendingName.current.value)
        }

    @Test
    fun `a name that fails to save stays pending without a failure state or a retry loop`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()
            profiles.saveResult = AppResult.Failure(AppError.Network())

            pendingName.expect("Ada Lovelace")
            pendingName.bind(googleUserId)
            profiles.profiles.emit(UserProfile.newFor(googleUserId, listOf("aa"), Instant.fromEpochSeconds(0)))
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(googleUserId), viewModel.state.value)
            assertEquals(1, profiles.saveCount())
            assertEquals(PendingDisplayName.Entry("Ada Lovelace", googleUserId), pendingName.current.value)
        }

    @Test
    fun `a profile that already carries the pending name is not written again`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            pendingName.expect("Ada Lovelace")
            pendingName.bind(userId)
            val named = UserProfile.newFor(userId, listOf("aa"), Instant.fromEpochSeconds(0))
            profiles.profiles.emit(named.copy(displayName = "Ada Lovelace"))
            advanceUntilIdle()

            assertEquals(0, profiles.saveCount())
            assertNull(pendingName.current.value)
        }

    @Test
    fun `a profile write failing after a switch is a switch failure and not a cold-start one`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()

            profiles.saveResult = AppResult.Failure(AppError.Unauthorized())
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            assertEquals(SessionUiState.SwitchFailed(UNAUTHORIZED_MESSAGE), viewModel.state.value)
        }

    @Test
    fun `retrying a failed profile write after a switch neither signs in again nor restarts the listener`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()
            profiles.saveResult = AppResult.Failure(AppError.Unauthorized())
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            profiles.saveResult = AppResult.Success(Unit)
            viewModel.retry()
            // A second tap while the first is in flight must not start a second write.
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(googleUserId), viewModel.state.value)
            assertEquals(1, sessions.signInCount)
            assertEquals(listOf(userId, googleUserId), profiles.observedUserIds)
            assertEquals(2, profiles.saveCount())
        }

    @Test
    fun `a profile write that fails again after a switch retry stays recoverable`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
            advanceUntilIdle()
            profiles.saveResult = AppResult.Failure(AppError.Unauthorized())
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(SessionUiState.SwitchFailed(UNAUTHORIZED_MESSAGE), viewModel.state.value)
            assertEquals(2, profiles.saveCount())
        }

    /** The retry's write is still open when the switch queues behind it: its failure belongs to nobody. */
    @Test
    fun `a profile write of the dropped uid failing mid-switch leaves no failure state behind`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            profiles.saveResult = AppResult.Failure(AppError.Unauthorized())
            profiles.errors.emit(AppError.NotFound("profile"))
            advanceUntilIdle()
            val inFlight = CompletableDeferred<AppResult<Unit>>()
            profiles.saveGates += inFlight

            viewModel.state.test {
                assertEquals(SessionUiState.Failed(UNAUTHORIZED_MESSAGE), awaitItem())
                viewModel.retry()
                assertEquals(SessionUiState.Loading, awaitItem())
                assertEquals(SessionUiState.Ready(userId), awaitItem())

                sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
                advanceUntilIdle()
                inFlight.complete(AppResult.Failure(AppError.Unauthorized()))
                advanceUntilIdle()

                assertEquals(SessionUiState.Ready(googleUserId), awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `a cold-start kitchen failure is still Failed and retry re-runs the bootstrap`() =
        runTest(dispatcher) {
            kitchens.readError = AppError.Network()
            val viewModel = viewModel()

            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            assertEquals(SessionUiState.Failed(UiText.of(Res.string.error_no_connection)), viewModel.state.value)

            kitchens.readError = null
            viewModel.retry()
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
            // The kitchen failed before the list was ever touched; the retry is what wrote it.
            assertEquals(1, lists.upsertCount)
        }

    @Test
    fun `retry does nothing while the session is Ready`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
            assertEquals(1, lists.upsertCount)
        }

    private fun viewModel(): SessionViewModel {
        val time = TimeProvider { Instant.fromEpochSeconds(0) }
        return SessionViewModel(
            ensureSession = EnsureSessionUseCase(sessions),
            ensureKitchen = EnsureKitchenUseCase(kitchens),
            ensureDefaultShoppingList = EnsureDefaultShoppingListUseCase(lists, IdGenerator { "list-1" }, time),
            observeSession = ObserveSessionUseCase(sessions),
            observeUserProfile = ObserveUserProfileUseCase(profiles),
            saveUserProfile = SaveUserProfileUseCase(profiles, FakeTaxonomyPort(), kitchens, time),
            pendingDisplayName = pendingName,
            time = time,
        )
    }
}

private val userId = (UserId.of("user-1") as AppResult.Success).data
private val googleUserId = (UserId.of("user-google") as AppResult.Success).data
private val UNAUTHORIZED_MESSAGE = UiText.of(Res.string.error_unauthorized_own_data)

/**
 * A [MutableStateFlow] on purpose: [EnsureSessionUseCase] reads `observeSession().first()`,
 * which an empty flow would suspend forever. A test can emit into it to move the auth state.
 */
private class FakeSessionPort : SessionRepositoryContract {
    var signIn: AppResult<Session.SignedIn> = AppResult.Success(Session.SignedIn(userId, isAnonymous = true))
    var signInCount = 0
    val sessionChanges = MutableStateFlow<Session>(Session.SignedOut)

    override fun observeSession(): Flow<Session> = sessionChanges

    override suspend fun signInAnonymously(): AppResult<Session.SignedIn> {
        signInCount++
        return signIn.also { result -> if (result is AppResult.Success) sessionChanges.value = result.data }
    }

    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AppResult<Session.SignedIn> =
        signIn.also { result -> if (result is AppResult.Success) sessionChanges.value = result.data }

    override suspend fun signOut(): AppResult<Unit> {
        sessionChanges.value = Session.SignedOut
        return AppResult.Success(Unit)
    }
}

/** It never keeps what it is given: a second bootstrap has to be visible as a second write. */
private class FakeShoppingListPort : ShoppingListRepositoryContract {
    var upsert: AppResult<Unit> = AppResult.Success(Unit)
    var upsertCount = 0

    override fun observeLists(kitchenId: KitchenId): Flow<List<ShoppingList>> = emptyFlow()

    override fun listErrors(kitchenId: KitchenId): Flow<AppError> = emptyFlow()

    override suspend fun getLists(kitchenId: KitchenId): AppResult<List<ShoppingList>> = AppResult.Success(emptyList())

    override suspend fun upsertList(
        kitchenId: KitchenId,
        list: ShoppingList,
    ): AppResult<Unit> {
        upsertCount++
        return upsert
    }
}

private class FakeUserProfilePort : UserProfileRepositoryContract {
    val profiles = MutableSharedFlow<UserProfile>(replay = 1)
    val errors = MutableSharedFlow<AppError>()
    var saveResult: AppResult<Unit> = AppResult.Success(Unit)
    var saved: UserProfile? = null

    private val counter = Mutex()
    private var saves = 0

    suspend fun saveCount(): Int = counter.withLock { saves }

    // Each queued gate holds one save open until the test completes it with that save's result.
    val saveGates = ArrayDeque<CompletableDeferred<AppResult<Unit>>>()

    val observedUserIds = mutableListOf<UserId>()

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
        counter.withLock { saves++ }
        saved = profile
        return saveGates.removeFirstOrNull()?.await() ?: saveResult
    }
}

/** An empty catalogue: a new profile carries no term to validate against it. */
