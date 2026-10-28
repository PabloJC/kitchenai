package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.seconds

/**
 * Today's reminder, worded from the pantry as it reads right now, for a job that wakes at the chosen
 * time with no screen open. Null when there is nothing to say, reminders are off, or a read never
 * answers: a missed reminder beats one about food that is already gone.
 */
class TodayReminder(
    private val session: ObserveSessionUseCase,
    private val reads: ExpiryReminderReadsDelegate,
    private val time: TimeProvider,
    private val wording: ReminderWording = ResourceReminderWording,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    suspend fun compose(languageTags: List<String>): ReminderText? =
        withTimeoutOrNull(READ_WAIT) {
            val signedIn = session().first() as? Session.SignedIn ?: return@withTimeoutOrNull null
            val settings = reads.settings().first()
            if (!settings.offered) return@withTimeoutOrNull null
            val kitchen = reads.kitchen(signedIn.userId).firstOrNull() ?: return@withTimeoutOrNull null
            val items = reads.pantry(kitchen.id).firstOrNull() ?: return@withTimeoutOrNull null
            val digest = reads.plan.today(items, settings, time.now(), zone()) ?: return@withTimeoutOrNull null
            // The catalogue only names lines; without it they fall back to their ids rather than to silence.
            val catalogue = withTimeoutOrNull(NAMES_WAIT) { reads.ingredients().firstOrNull() }.orEmpty()
            wording.describe(digest, namesFrom(catalogue, languageTags))
        }

    private companion object {
        val READ_WAIT = 20.seconds
        val NAMES_WAIT = 5.seconds
    }
}
