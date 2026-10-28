package com.kitchenai.ui.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kitchenai.ui.presentation.reminder.TodayReminder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The daily reminder: worded from the pantry as it is now, from the offline cache when there is no network. */
class ExpiryReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val today: TodayReminder by inject()

    override suspend fun doWork(): Result {
        // Nothing to say is a success: the job runs again tomorrow either way.
        val text = today.compose(platformLanguageTags()) ?: return Result.success()
        ExpiryReminderNotifier.post(applicationContext, text.title, text.body, text.publicBody, text.channelName)
        return Result.success()
    }
}
