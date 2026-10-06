package com.kitchenai.ui.presentation.session

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.kitchen.EnsureKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.KitchenMembershipLock
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** Keeping the user in a kitchen after a leave or a removal; see `docs/session.md`. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionKitchenProvisioningTest {
    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionPort()
    private val kitchens = FakeKitchenPort()
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
    fun `a kitchen that vanishes mid-session is provisioned again without a restart`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            kitchens.dropKitchen()
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
            assertEquals(1, kitchens.createCount)
            // The default list is ensured for the new kitchen too, so Shopping has one to open.
            assertEquals(2, lists.upsertCount)
        }

    @Test
    fun `the replacement kitchen carries the profile display name`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            val named = UserProfile.newFor(userId, listOf("aa"), Instant.fromEpochSeconds(0)).copy(displayName = "Ada")
            profiles.profiles.emit(named)
            advanceUntilIdle()

            kitchens.dropKitchen()
            advanceUntilIdle()

            assertEquals(listOf<String?>("Ada"), kitchens.createdAs)
        }

    @Test
    fun `the same report arriving twice provisions one kitchen`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            kitchens.dropKitchen()
            kitchens.reportNotFound()
            advanceUntilIdle()

            assertEquals(1, kitchens.createCount)
        }

    @Test
    fun `a kitchen that exists is never replaced because the listener reported none`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            kitchens.reportNotFound()
            advanceUntilIdle()

            assertEquals(0, kitchens.createCount)
            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
        }

    @Test
    fun `a listener failure that is not a missing kitchen provisions nothing`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()

            kitchens.report(AppError.Network())
            advanceUntilIdle()

            assertEquals(0, kitchens.createCount)
            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
        }

    @Test
    fun `a failed re-provisioning is recoverable and the retry keeps the profile listener`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            kitchens.createError = AppError.Network()

            kitchens.dropKitchen()
            advanceUntilIdle()
            assertEquals(SessionUiState.SwitchFailed(UiText.of(Res.string.error_no_connection)), viewModel.state.value)

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(SessionUiState.Ready(userId), viewModel.state.value)
            assertEquals(2, kitchens.createCount)
            assertEquals(listOf(userId), profiles.observedUserIds)
            assertEquals(1, sessions.signInCount)
        }

    /** The create is still open when the uid moves on: it is dropped, and the new uid's setup is not held up by it. */
    @Test
    fun `a re-provisioning in flight when the uid moves on neither blocks nor fails the switch`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            kitchens.createGate = CompletableDeferred()

            viewModel.state.test {
                assertEquals(SessionUiState.Ready(userId), awaitItem())
                kitchens.dropKitchen()
                advanceUntilIdle()
                sessions.sessionChanges.emit(Session.SignedIn(googleUserId, isAnonymous = false))
                advanceUntilIdle()
                kitchens.createGate?.complete(Unit)
                advanceUntilIdle()

                assertEquals(SessionUiState.Ready(googleUserId), awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `a report arriving while the shell is down provisions nothing on its own`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.start(listOf("aa"), "list")
            advanceUntilIdle()
            kitchens.createError = AppError.Network()
            kitchens.dropKitchen()
            advanceUntilIdle()
            assertEquals(SessionUiState.SwitchFailed(UiText.of(Res.string.error_no_connection)), viewModel.state.value)

            kitchens.reportNotFound()
            advanceUntilIdle()

            // Only the retry provisions again: the first attempt is the one create so far.
            assertEquals(1, kitchens.createCount)
        }

    private fun viewModel(): SessionViewModel {
        val time = TimeProvider { Instant.fromEpochSeconds(0) }
        return SessionViewModel(
            ensureSession = EnsureSessionUseCase(sessions),
            kitchen =
                SessionKitchenDelegate(
                    ensure = EnsureKitchenUseCase(kitchens, KitchenMembershipLock()),
                    ensureDefaultList = EnsureDefaultShoppingListUseCase(lists, IdGenerator { "list-1" }, time),
                    observe = ObserveKitchenUseCase(kitchens),
                ),
            observeSession = ObserveSessionUseCase(sessions),
            observeUserProfile = ObserveUserProfileUseCase(profiles),
            saveUserProfile = SaveUserProfileUseCase(profiles, FakeTaxonomyPort(), kitchens, time),
            pendingDisplayName = PendingDisplayName(),
            time = time,
        )
    }
}
