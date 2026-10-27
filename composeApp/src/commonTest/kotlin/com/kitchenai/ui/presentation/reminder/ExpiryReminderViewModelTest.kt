package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.Quantity
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.ReminderSettingsRepositoryContract
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.pantry.ObservePantryUseCase
import com.kitchenai.shared.domain.usecase.reminder.ObserveReminderSettingsUseCase
import com.kitchenai.shared.domain.usecase.reminder.PlanExpiryDigestsUseCase
import com.kitchenai.shared.domain.usecase.reminder.SaveReminderSettingsUseCase
import com.kitchenai.ui.platform.ExpiryReminderScheduler
import com.kitchenai.ui.platform.ScheduledReminder
import com.kitchenai.ui.presentation.common.FakeKitchenPort
import com.kitchenai.ui.presentation.common.FakePantryPort
import com.kitchenai.ui.presentation.common.defaultKitchenId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExpiryReminderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = TimeZone.of("Europe/Madrid")
    private val now = LocalDateTime(2026, 10, 20, 9, 0).toInstant(zone)
    private val user = (UserId.of("user-1") as AppResult.Success).data
    private val settings = FakeSettingsRepository()
    private val scheduler = RecordingScheduler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a dated item raises the one-time question and nothing is scheduled before the answer`() =
        runTest(dispatcher) {
            val viewModel = started(FakePantryPort(listOf(item("a", tomorrow()))))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.offering)
            assertNull(scheduler.next)
        }

    @Test
    fun `an undated pantry asks nothing`() =
        runTest(dispatcher) {
            val viewModel = started(FakePantryPort(listOf(item("a", null))))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.offering)
        }

    @Test
    fun `a yes with the permission granted schedules today's digest and is not asked again`() =
        runTest(dispatcher) {
            val viewModel = started(FakePantryPort(listOf(item("a", tomorrow()))))
            advanceUntilIdle()

            viewModel.answered(granted = true)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.offering)
            assertEquals(ReminderSettings(enabled = true, offered = true), settings.current.value)
            assertEquals(LocalDateTime(2026, 10, 20, 18, 0).toInstant(zone), scheduler.next?.fireAt)
            assertEquals("1 expiring, 0 expired", scheduler.next?.body)
            assertEquals(1, scheduler.pending.size)
        }

    @Test
    fun `a no turns reminders off for good`() =
        runTest(dispatcher) {
            val viewModel = started(FakePantryPort(listOf(item("a", tomorrow()))))
            advanceUntilIdle()

            viewModel.decline()
            advanceUntilIdle()

            assertEquals(ReminderSettings(enabled = false, offered = true), settings.current.value)
            assertFalse(viewModel.state.value.offering)
            assertNull(scheduler.next)
            assertTrue(scheduler.cancelled > 0)
        }

    @Test
    fun `a yes with the permission refused stores reminders off`() =
        runTest(dispatcher) {
            val viewModel = started(FakePantryPort(listOf(item("a", tomorrow()))))
            advanceUntilIdle()

            viewModel.answered(granted = false)
            advanceUntilIdle()

            assertEquals(ReminderSettings(enabled = false, offered = true), settings.current.value)
            assertNull(scheduler.next)
        }

    @Test
    fun `dismissing postpones the question without storing an answer`() =
        runTest(dispatcher) {
            val pantry = FakePantryPort(listOf(item("a", tomorrow())))
            val viewModel = started(pantry)
            advanceUntilIdle()

            viewModel.later()
            pantry.upsert(defaultKitchenId, item("b", tomorrow()))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.offering)
            assertEquals(ReminderSettings(), settings.current.value)
        }

    @Test
    fun `using up the last expiring item cancels the pending reminder`() =
        runTest(dispatcher) {
            settings.current.value = ReminderSettings(offered = true)
            val pantry = FakePantryPort(listOf(item("a", tomorrow())))
            started(pantry)
            advanceUntilIdle()
            assertEquals(1, scheduler.scheduledCount)

            pantry.remove(defaultKitchenId, (PantryItemId.of("a") as AppResult.Success).data)
            advanceUntilIdle()

            assertNull(scheduler.next)
        }

    @Test
    fun `another user replaces the previous listener instead of adding one`() =
        runTest(dispatcher) {
            settings.current.value = ReminderSettings(offered = true)
            val pantry = FakePantryPort(listOf(item("a", tomorrow())))
            val viewModel = started(pantry)
            advanceUntilIdle()

            viewModel.start((UserId.of("user-2") as AppResult.Success).data)
            advanceUntilIdle()
            val afterSwap = scheduler.scheduledCount
            pantry.upsert(defaultKitchenId, item("b", tomorrow()))
            advanceUntilIdle()

            assertEquals(afterSwap + 1, scheduler.scheduledCount)
        }

    private fun tomorrow(): Instant = LocalDateTime(2026, 10, 21, 12, 0).toInstant(zone)

    private fun started(pantry: FakePantryPort): ExpiryReminderViewModel =
        ExpiryReminderViewModel(
            reads =
                ExpiryReminderReadsDelegate(
                    kitchen = ObserveKitchenUseCase(FakeKitchenPort()),
                    pantry = ObservePantryUseCase(pantry),
                    settings = ObserveReminderSettingsUseCase(settings),
                    plan = PlanExpiryDigestsUseCase(),
                ),
            save = SaveReminderSettingsUseCase(settings),
            scheduler = scheduler,
            time = TimeProvider { now },
            wording = {
                    digest ->
                ReminderText("title", "${digest.expiringCount} expiring, ${digest.expiredCount} expired", "channel")
            },
            zone = { zone },
        ).also { it.start(user) }

    private fun item(
        id: String,
        expiresAt: Instant?,
    ): PantryItem =
        PantryItem(
            id = (PantryItemId.of(id) as AppResult.Success).data,
            ingredient = (IngredientId.of("ing-$id") as AppResult.Success).data,
            freeText = null,
            quantity = Quantity(1.0),
            location = null,
            expiresAt = expiresAt,
            updatedAt = now,
        )
}

private class FakeSettingsRepository : ReminderSettingsRepositoryContract {
    val current = MutableStateFlow(ReminderSettings())

    override fun observeSettings(): Flow<ReminderSettings> = current

    override suspend fun saveSettings(settings: ReminderSettings): AppResult<Unit> {
        current.value = settings
        return AppResult.Success(Unit)
    }
}

private class RecordingScheduler : ExpiryReminderScheduler {
    var pending: List<ScheduledReminder> = emptyList()
        private set
    val next: ScheduledReminder? get() = pending.firstOrNull()
    var scheduledCount = 0
        private set
    var cancelled = 0
        private set

    override fun schedule(reminders: List<ScheduledReminder>) {
        pending = reminders
        scheduledCount++
    }

    override fun cancel() {
        pending = emptyList()
        cancelled++
    }
}
