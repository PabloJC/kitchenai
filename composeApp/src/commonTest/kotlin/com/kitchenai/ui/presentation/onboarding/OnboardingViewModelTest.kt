package com.kitchenai.ui.presentation.onboarding

import app.cash.turbine.test
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.IdGenerator
import com.kitchenai.shared.domain.port.OnboardingRepositoryContract
import com.kitchenai.shared.domain.port.SessionRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.kitchen.JoinKitchenUseCase
import com.kitchenai.shared.domain.usecase.kitchen.KitchenMembershipLock
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.onboarding.CompleteOnboardingUseCase
import com.kitchenai.shared.domain.usecase.onboarding.ObserveOnboardingCompletedUseCase
import com.kitchenai.shared.domain.usecase.pantry.AddStaplesUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithAppleUseCase
import com.kitchenai.shared.domain.usecase.session.SignInWithGoogleUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToAppleAccountUseCase
import com.kitchenai.shared.domain.usecase.session.SwitchToGoogleAccountUseCase
import com.kitchenai.ui.presentation.common.FakeIngredientPort
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.PendingDisplayName
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.kitchen
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.kitchen_invalid_code
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = (UserId.of("user-1") as AppResult.Success).data
    private val onboarding = FakeOnboardingPort()
    private val catalogue = FakeIngredientPort()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a device that has been through it goes straight to the app`() =
        runTest(dispatcher) {
            onboarding.completed.value = true

            val viewModel = started()
            advanceUntilIdle()

            assertEquals(OnboardingStatus.DONE, viewModel.state.value.status)
        }

    @Test
    fun `a new device with an empty pantry shows the first page`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            assertEquals(OnboardingStatus.SHOWING, viewModel.state.value.status)
            assertEquals(OnboardingStep.VALUE, viewModel.state.value.step)
        }

    @Test
    fun `a kitchen that already holds something is an existing user and is marked done unseen`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = FakePantryPort(listOf(holding("rice"))))
            advanceUntilIdle()

            assertEquals(OnboardingStatus.DONE, viewModel.state.value.status)
            assertTrue(onboarding.completed.value)
        }

    @Test
    fun `a kitchen listener that ends without a word still shows the flow`() =
        runTest(dispatcher) {
            val viewModel = started(kitchens = FakeKitchenPort(initial = null))
            advanceUntilIdle()

            assertEquals(OnboardingStatus.SHOWING, viewModel.state.value.status)
        }

    @Test
    fun `the staples are the catalogue by name and a tap marks one chosen`() =
        runTest(dispatcher) {
            val viewModel = started()
            catalogue.emit(listOf(ingredient("rice", "Rice"), ingredient("egg", "Egg")))
            advanceUntilIdle()

            viewModel.toggle(id("rice"))

            assertEquals(listOf("Egg", "Rice"), viewModel.state.value.staples.map { it.label })
            assertEquals(listOf(false, true), viewModel.state.value.staples.map { it.isChosen })
            assertEquals(1, viewModel.state.value.chosenCount)
        }

    @Test
    fun `leaving the staples page adds the chosen ones and moves on`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort()
            val viewModel = started(pantry = pantry)
            catalogue.emit(listOf(ingredient("rice", "Rice"), ingredient("egg", "Egg")))
            advanceUntilIdle()

            viewModel.next()
            viewModel.toggle(id("rice"))
            viewModel.toggle(id("egg"))
            viewModel.next()
            advanceUntilIdle()

            assertEquals(OnboardingStep.PREFERENCES, viewModel.state.value.step)
            assertEquals(setOf("rice", "egg"), pantry.held.mapNotNull { it.ingredient?.value }.toSet())
        }

    @Test
    fun `nothing chosen moves on without writing`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort()
            val viewModel = started(pantry = pantry)
            advanceUntilIdle()

            viewModel.next()
            viewModel.next()
            advanceUntilIdle()

            assertEquals(OnboardingStep.PREFERENCES, viewModel.state.value.step)
            assertTrue(pantry.held.isEmpty())
        }

    @Test
    fun `a refused add stays on the staples page and says why`() =
        runTest(dispatcher) {
            val viewModel = started(pantry = FakePantryPort(readError = AppError.Network()))
            catalogue.emit(listOf(ingredient("rice", "Rice")))
            advanceUntilIdle()

            viewModel.next()
            viewModel.toggle(id("rice"))
            viewModel.next()
            advanceUntilIdle()

            assertEquals(OnboardingStep.STAPLES, viewModel.state.value.step)
            assertNotNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isAdding)
        }

    @Test
    fun `back returns a page and does nothing on the first`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            viewModel.back()
            assertEquals(OnboardingStep.VALUE, viewModel.state.value.step)
            viewModel.next()
            viewModel.back()
            assertEquals(OnboardingStep.VALUE, viewModel.state.value.step)
        }

    @Test
    fun `skipping from any page ends it for good and opens ideas without adding what was chosen`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort()
            val viewModel = started(pantry = pantry)
            catalogue.emit(listOf(ingredient("rice", "Rice")))
            advanceUntilIdle()
            viewModel.next()
            viewModel.toggle(id("rice"))

            viewModel.exits.test {
                viewModel.skip()
                assertEquals(OnboardingExit.IDEAS, awaitItem())
            }

            assertEquals(OnboardingStatus.DONE, viewModel.state.value.status)
            assertTrue(onboarding.completed.value)
            assertTrue(pantry.held.isEmpty())
        }

    @Test
    fun `setting preferences ends it and opens the profile`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()
            viewModel.next()
            viewModel.next()
            advanceUntilIdle()

            viewModel.exits.test {
                viewModel.setPreferences()
                assertEquals(OnboardingExit.PREFERENCES, awaitItem())
            }
            assertTrue(onboarding.completed.value)
        }

    @Test
    fun `not now on the last page opens ideas`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()
            viewModel.next()
            viewModel.next()
            advanceUntilIdle()

            viewModel.exits.test {
                viewModel.next()
                assertEquals(OnboardingExit.IDEAS, awaitItem())
            }
        }

    private val sessions = OnboardingSessionPort()
    private val pendingName = PendingDisplayName()

    @Test
    fun `signing in from the first page links the guest and opens the pantry with no question asked`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()

            viewModel.exits.test {
                viewModel.signInWithGoogle(GoogleIdToken("token"), "word-a")
                assertEquals(OnboardingExit.PANTRY, awaitItem())
            }

            assertEquals(listOf("link"), sessions.calls)
            assertTrue(onboarding.completed.value)
            assertEquals(PendingDisplayName.Entry("word-a", user), pendingName.current.value)
        }

    @Test
    fun `an account that already has a kitchen is switched to at once without the stays-behind dialog`() =
        runTest(dispatcher) {
            sessions.link = AppResult.Failure(AppError.Conflict())
            val viewModel = started()
            advanceUntilIdle()

            viewModel.exits.test {
                viewModel.signInWithGoogle(GoogleIdToken("token"), null)
                assertEquals(OnboardingExit.PANTRY, awaitItem())
            }

            assertEquals(listOf("link", "sign-in"), sessions.calls)
        }

    @Test
    fun `an Apple conflict asks for the sheet once more and the second token switches`() =
        runTest(dispatcher) {
            sessions.link = AppResult.Failure(AppError.Conflict())
            val viewModel = started()
            advanceUntilIdle()

            viewModel.signInWithApple(AppleIdToken("token"), AppleRawNonce("nonce"), null)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.appleSwitchPending)
            assertEquals(OnboardingStatus.SHOWING, viewModel.state.value.status)

            viewModel.exits.test {
                viewModel.switchToAppleAccount(AppleIdToken("token-2"), AppleRawNonce("nonce-2"), null)
                assertEquals(OnboardingExit.PANTRY, awaitItem())
            }
            assertFalse(viewModel.state.value.appleSwitchPending)
        }

    @Test
    fun `a failed sign-in stays on the first page and says why`() =
        runTest(dispatcher) {
            sessions.link = AppResult.Failure(AppError.Network())
            val viewModel = started()
            advanceUntilIdle()

            viewModel.signInWithGoogle(GoogleIdToken("token"), "word-a")
            advanceUntilIdle()

            assertEquals(OnboardingStatus.SHOWING, viewModel.state.value.status)
            assertNotNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.isBusy)
            assertEquals(null, pendingName.current.value)
        }

    @Test
    fun `an invite code joins without the leave-your-kitchen question and opens the pantry`() =
        runTest(dispatcher) {
            val kitchens = FakeKitchenPort().apply { joinResult = AppResult.Success(kitchen()) }
            val viewModel = started(kitchens = kitchens)
            advanceUntilIdle()
            viewModel.showJoin()
            assertEquals(OnboardingStep.JOIN, viewModel.state.value.step)

            viewModel.onJoinCodeChange("Join my kitchen on KitchenAI with code $CODE")
            assertEquals(CODE.uppercase(), viewModel.state.value.joinCode)
            viewModel.exits.test {
                viewModel.join()
                assertEquals(OnboardingExit.PANTRY, awaitItem())
            }

            assertEquals(1, kitchens.joinCalls.size)
        }

    @Test
    fun `a code that matches no kitchen stays on the join page and says so`() =
        runTest(dispatcher) {
            val refused = AppResult.Failure(AppError.NotFound("kitchenInvite"))
            val kitchens = FakeKitchenPort().apply { joinResult = refused }
            val viewModel = started(kitchens = kitchens)
            advanceUntilIdle()
            viewModel.showJoin()
            viewModel.onJoinCodeChange(CODE)

            viewModel.join()
            advanceUntilIdle()

            assertEquals(UiText.of(Res.string.kitchen_invalid_code), viewModel.state.value.error)
            assertEquals(OnboardingStep.JOIN, viewModel.state.value.step)
            assertEquals(OnboardingStatus.SHOWING, viewModel.state.value.status)
        }

    @Test
    fun `back from the join page returns to the first page`() =
        runTest(dispatcher) {
            val viewModel = started()
            advanceUntilIdle()
            viewModel.showJoin()

            viewModel.back()

            assertEquals(OnboardingStep.VALUE, viewModel.state.value.step)
        }

    private fun started(
        kitchens: FakeKitchenPort = FakeKitchenPort(),
        pantry: FakePantryPort = FakePantryPort(),
    ): OnboardingViewModel {
        var next = 0
        return OnboardingViewModel(
            reads =
                OnboardingReadsDelegate(
                    completed = ObserveOnboardingCompletedUseCase(onboarding),
                    kitchen = ObserveKitchenUseCase(kitchens),
                    pantry = ObservePantryUseCase(pantry),
                    ingredients = ObserveIngredientsUseCase(catalogue),
                ),
            writes =
                OnboardingWritesDelegate(
                    complete = CompleteOnboardingUseCase(onboarding),
                    addStaples = AddStaplesUseCase(pantry, IdGenerator { "item-${++next}" }, TimeProvider { now }),
                    join = JoinKitchenUseCase(kitchens, KitchenMembershipLock()),
                ),
            account =
                OnboardingAccountDelegate(
                    signInWithGoogle = SignInWithGoogleUseCase(sessions),
                    switchToGoogleAccount = SwitchToGoogleAccountUseCase(sessions),
                    signInWithApple = SignInWithAppleUseCase(sessions),
                    switchToAppleAccount = SwitchToAppleAccountUseCase(sessions),
                    pendingDisplayName = pendingName,
                ),
        ).also { it.start(user, listOf("en")) }
    }

    private fun id(raw: String): IngredientId = (IngredientId.of(raw) as AppResult.Success).data

    private fun ingredient(
        raw: String,
        label: String,
    ): Ingredient = Ingredient(id(raw), mapOf("en" to label), defaultUnit = null, tags = emptyList())

    private fun holding(raw: String): PantryItem =
        PantryItem(
            id = (PantryItemId.of("held-$raw") as AppResult.Success).data,
            ingredient = id(raw),
            freeText = null,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = null,
            updatedAt = now,
        )

    private companion object {
        val now: Instant = Instant.fromEpochSeconds(0)
    }
}

private class FakeOnboardingPort : OnboardingRepositoryContract {
    val completed = MutableStateFlow(false)

    override fun observeCompleted(): Flow<Boolean> = completed

    override suspend fun markCompleted(): AppResult<Unit> {
        completed.value = true
        return AppResult.Success(Unit)
    }
}

/** Opaque, in the shape a real code has: a UUID. */
private const val CODE = "0a1b2c3d-0000-4000-8000-00000000abcd"

/** A guest whose link can be refused while a plain sign-in still succeeds, as an account with its own uid is. */
private class OnboardingSessionPort : SessionRepositoryContract {
    private val guest = Session.SignedIn((UserId.of("user-1") as AppResult.Success).data, isAnonymous = true)
    var link: AppResult<Session.SignedIn> = AppResult.Success(guest.copy(isAnonymous = false))
    val calls = mutableListOf<String>()

    override fun observeSession(): Flow<Session> = MutableStateFlow(guest)

    override suspend fun signInAnonymously(): AppResult<Session.SignedIn> = AppResult.Success(guest)

    override suspend fun signInWithGoogle(
        idToken: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> = signedIn()

    override suspend fun linkWithGoogle(
        idToken: GoogleIdToken,
        accessToken: GoogleAccessToken?,
    ): AppResult<Session.SignedIn> = linked()

    override suspend fun signInWithApple(
        idToken: AppleIdToken,
        rawNonce: AppleRawNonce,
    ): AppResult<Session.SignedIn> = signedIn()

    override suspend fun linkWithApple(
        idToken: AppleIdToken,
        rawNonce: AppleRawNonce,
    ): AppResult<Session.SignedIn> = linked()

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    private fun linked(): AppResult<Session.SignedIn> = link.also { calls += "link" }

    private fun signedIn(): AppResult<Session.SignedIn> =
        AppResult.Success(Session.SignedIn((UserId.of("user-2") as AppResult.Success).data, isAnonymous = false))
            .also { calls += "sign-in" }
}
