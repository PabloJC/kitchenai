package com.kitchenai.ui.presentation.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.reminder.SaveReminderSettingsUseCase
import com.kitchenai.ui.platform.ExpiryReminderScheduler
import com.kitchenai.ui.platform.ScheduledReminder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Keeps the system's one pending expiry reminder in step with the kitchen's pantry: every
 * snapshot replans it, so an item used up or re-dated never leaves a stale reminder behind.
 * Nothing is scheduled until the person has said yes to the one-time question.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpiryReminderViewModel(
    private val reads: ExpiryReminderReadsDelegate,
    private val save: SaveReminderSettingsUseCase,
    private val scheduler: ExpiryReminderScheduler,
    private val time: TimeProvider,
    private val wording: ReminderWording = ResourceReminderWording,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {
    private val internalState = MutableStateFlow(ExpiryReminderUiState())
    val state: StateFlow<ExpiryReminderUiState> = internalState.asStateFlow()

    private var started = false
    private var current = ReminderSettings()

    // Dismissed without an answer: not asked again until the next launch, and nothing is stored.
    private var postponed = false

    /** Idempotent: the shell composes again on rotation, not a second set of listeners. */
    fun start(userId: UserId) {
        if (started) return
        started = true
        viewModelScope.launch {
            val pantry = reads.kitchen(userId).flatMapLatest { kitchen -> reads.pantry(kitchen.id) }
            combine(pantry, reads.settings(), ::Pair).collectLatest { (items, settings) ->
                current = settings
                internalState.update {
                    it.copy(
                        offering =
                            !settings.offered &&
                                items.any {
                                        item ->
                                    item.expiresAt != null
                                },
                    )
                }
                val digest = if (settings.offered) reads.plan(items, settings, time.now(), zone()) else null
                if (digest == null) {
                    scheduler.cancel()
                } else {
                    val text = wording.describe(digest)
                    scheduler.schedule(ScheduledReminder(digest.fireAt, text.title, text.body, text.channelName))
                }
            }
        }
    }

    /** The answer to the system prompt: a no keeps them off, and neither is asked again. */
    fun answered(granted: Boolean) = store(current.copy(offered = true, enabled = granted))

    fun decline() = store(current.copy(offered = true, enabled = false))

    fun later() {
        postponed = true
        internalState.update { it.copy(offering = false) }
    }

    // A failed write only means the question comes back next launch.
    private fun store(next: ReminderSettings) {
        internalState.update { it.copy(offering = false) }
        viewModelScope.launch { save(next) }
    }
}
