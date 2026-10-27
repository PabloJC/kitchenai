package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.model.ExpiryDigest
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.reminder_channel
import com.kitchenai.ui.resources.reminder_expired
import com.kitchenai.ui.resources.reminder_expiring
import com.kitchenai.ui.resources.reminder_title
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Counts only: a lock screen is no place to list what is in someone's fridge. */
object ResourceReminderWording : ReminderWording {
    override suspend fun describe(digest: ExpiryDigest): ReminderText {
        val parts =
            listOfNotNull(
                digest.expiringCount.takeIf { it > 0 }?.let { getPluralString(Res.plurals.reminder_expiring, it, it) },
                digest.expiredCount.takeIf { it > 0 }?.let { getPluralString(Res.plurals.reminder_expired, it, it) },
            )
        return ReminderText(
            title = getString(Res.string.reminder_title),
            body = parts.joinToString(separator = " · "),
            channelName = getString(Res.string.reminder_channel),
        )
    }
}
