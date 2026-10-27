package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.model.ExpiryDigest

/** Words a digest. An interface so tests need no resource bundle. */
fun interface ReminderWording {
    suspend fun describe(digest: ExpiryDigest): ReminderText
}
