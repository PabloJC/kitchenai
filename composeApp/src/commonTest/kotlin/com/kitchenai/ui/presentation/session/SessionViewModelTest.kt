package com.kitchenai.ui.presentation.session

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.ShoppingList
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.port.ShoppingListRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.port.UserProfileRepositoryContract
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfile
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfile
import com.kitchenai.shared.domain.usecase.session.EnsureSession
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingList
import com.kitchenai.ui.presentation.common.FakeTaxonomyPort
import com.kitchenai.ui.presentation.common.TestDispatcherProvider
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_unauthorized_own_data
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionPort()
    private val lists = FakeShoppingListPort()
    private val profiles = FakeUserProfilePort()

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

    /**
     * The retry path after `Ready`, which had no coverage: the listener from the first attempt
     * is still subscribed, so it and the bootstrap both reach `createProfile`.
     *
     * It runs on the test dispatcher rather than a real pool. An earlier version used
     * `Dispatchers.Default` and a fixed settle delay, which made it flaky on a loaded CI
     * machine, and it never proved the lock in `createProfile` anyway: that window is a few
     * instructions with no suspension point, which no unit test can interleave on demand.
     */
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

    private fun viewModel(default: CoroutineDispatcher = dispatcher): SessionViewModel {
        val time = TimeProvider { Instant.fromEpochSeconds(0) }
        return SessionViewModel(
            ensureSession = EnsureSession(sessions),
            ensureDefaultShoppingList = EnsureDefaultShoppingList(lists, IdGenerator { "list-1" }, time),
            observeUserProfile = ObserveUserProfile(profiles),
            saveUserProfile = SaveUserProfile(profiles, FakeTaxonomyPort(), time),
            time = time,
            dispatchers = TestDispatcherProvider(dispatcher, default),
        )
    }
}

private val userId = (UserId.of("user-1") as AppResult.Success).data
private val UNAUTHORIZED_MESSAGE = UiText.of(Res.string.error_unauthorized_own_data)

private class FakeSessionPort : SessionRepositoryContract {
    var signIn: AppResult<Session.SignedIn> = AppResult.Success(Session.SignedIn(userId, isAnonymous = true))
    var signInCount = 0

    override fun observeSession(): Flow<Session> = flowOf(Session.SignedOut)

    override suspend fun signInAnonymously(): AppResult<Session.SignedIn> {
        signInCount++
        return signIn
    }

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)
}

/** It never keeps what it is given: a second bootstrap has to be visible as a second write. */
private class FakeShoppingListPort : ShoppingListRepositoryContract {
    var upsert: AppResult<Unit> = AppResult.Success(Unit)
    var upsertCount = 0

    override fun observeLists(userId: UserId): Flow<List<ShoppingList>> = emptyFlow()

    override fun listErrors(userId: UserId): Flow<AppError> = emptyFlow()

    override suspend fun getLists(userId: UserId): AppResult<List<ShoppingList>> = AppResult.Success(emptyList())

    override suspend fun upsertList(
        userId: UserId,
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

    // Counted under a lock: the concurrency test writes from more than one thread, and an
    // undercount there would hide the very bug that test exists to catch.
    private val counter = Mutex()
    private var saves = 0

    suspend fun saveCount(): Int = counter.withLock { saves }

    override fun observeProfile(userId: UserId): Flow<UserProfile> = profiles

    override fun profileErrors(userId: UserId): Flow<AppError> = errors

    override suspend fun save(profile: UserProfile): AppResult<Unit> {
        counter.withLock { saves++ }
        saved = profile
        return saveResult
    }
}

/** An empty catalogue: a new profile carries no term to validate against it. */
