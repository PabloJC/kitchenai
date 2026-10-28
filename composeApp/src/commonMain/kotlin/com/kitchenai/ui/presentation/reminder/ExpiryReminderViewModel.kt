package com.kitchenai.ui.presentation.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ReminderSettings
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.reminder.SaveReminderSettingsUseCase
import com.kitchenai.ui.platform.ExpiryReminderScheduler
import com.kitchenai.ui.platform.ScheduledReminder
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.clockTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Keeps the system's pending expiry reminders in step with the kitchen's pantry: every
 * snapshot replans them, so an item used up or re-dated never leaves a stale reminder behind.
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
    private val languageTags: () -> List<String> = { platformLanguageTags() },
) : ViewModel() {
    private val internalState = MutableStateFlow(ExpiryReminderUiState())
    val state: StateFlow<ExpiryReminderUiState> = internalState.asStateFlow()

    private var startedFor: UserId? = null
    private var following: Job? = null

    // Dismissed without an answer: not asked again until the next launch, and nothing is stored.
    private var postponed = false

    /** Idempotent per user: the shell composes again on rotation; a uid swap follows the new kitchen. */
    fun start(userId: UserId) {
        if (userId == startedFor) return
        startedFor = userId
        following?.cancel()
        following = viewModelScope.launch { follow(userId) }
    }

    private suspend fun follow(userId: UserId) {
        val pantry = reads.kitchen(userId).flatMapLatest { kitchen -> reads.pantry(kitchen.id) }
        // The catalogue only names lines: it starts empty so the first plan never waits on it.
        val catalogue = reads.ingredients().onStart { emit(emptyList()) }
        combine(pantry, reads.settings(), catalogue, ::Triple).collectLatest { (items, settings, known) ->
            val dated = items.any { item -> item.expiresAt != null }
            internalState.update {
                it.copy(
                    offering = !postponed && !settings.offered && dated,
                    time = clockTime(settings.hour, settings.minute),
                )
            }
            // Kept going while reminders are on, even on a quiet day: Android's job reads the pantry itself.
            if (!settings.offered || !settings.enabled) {
                scheduler.cancel()
                return@collectLatest
            }
            val names = namesFrom(known, languageTags())
            val reminders =
                reads.plan(items, settings, time.now(), zone()).map { digest ->
                    val text = wording.describe(digest, names)
                    ScheduledReminder(digest.fireAt, text.title, text.body, text.publicBody, text.channelName)
                }
            scheduler.schedule(reminders, settings.hour, settings.minute)
        }
    }

    /** The answer to the system prompt: a no keeps them off, and neither is asked again. */
    fun answered(granted: Boolean) = store { it.copy(offered = true, enabled = granted) }

    fun decline() = store { it.copy(offered = true, enabled = false) }

    fun later() {
        postponed = true
        internalState.update { it.copy(offering = false) }
    }

    // Read inside the scope: the iOS permission callback arrives off the main thread. A failed write asks again.
    private fun store(change: (ReminderSettings) -> ReminderSettings) {
        internalState.update { it.copy(offering = false) }
        viewModelScope.launch {
            if (save(change(reads.settings().first())) is AppResult.Failure) {
                internalState.update { it.copy(offering = true) }
            }
        }
    }
}
