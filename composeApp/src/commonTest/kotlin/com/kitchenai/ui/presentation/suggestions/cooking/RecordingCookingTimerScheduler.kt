package com.kitchenai.ui.presentation.suggestions.cooking

import com.kitchenai.ui.platform.CookingAlarm
import com.kitchenai.ui.platform.CookingTimerScheduler

/** Remembers what would have been handed to the system, keyed by id as the real schedulers are. */
class RecordingCookingTimerScheduler : CookingTimerScheduler {
    val pending = mutableMapOf<String, CookingAlarm>()
    val cancelled = mutableListOf<String>()

    override fun schedule(alarm: CookingAlarm) {
        pending[alarm.id] = alarm
    }

    override fun cancel(id: String) {
        pending.remove(id)
        cancelled += id
    }
}
