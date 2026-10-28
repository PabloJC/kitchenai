package com.kitchenai.ui.presentation.reminder

import com.kitchenai.shared.domain.model.ExpiryDigest
import com.kitchenai.shared.domain.model.PantryItem
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.reminder_channel
import com.kitchenai.ui.resources.reminder_expired
import com.kitchenai.ui.resources.reminder_expiring
import com.kitchenai.ui.resources.reminder_expiring_named
import com.kitchenai.ui.resources.reminder_names_more
import com.kitchenai.ui.resources.reminder_names_three
import com.kitchenai.ui.resources.reminder_names_two
import com.kitchenai.ui.resources.reminder_title
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Up to [NAMED] items by name and the rest as a count, so the reminder still makes sense if one was
 * used up since: the pantry it opens on shows the current truth. The lock screen gets counts only.
 */
object ResourceReminderWording : ReminderWording {
    private const val NAMED = 3

    override suspend fun describe(
        digest: ExpiryDigest,
        nameOf: (PantryItem) -> String,
    ): ReminderText {
        val expired =
            digest.expiredCount.takeIf { it > 0 }?.let {
                getPluralString(
                    Res.plurals.reminder_expired,
                    it,
                    it,
                )
            }
        val counted =
            digest.expiringCount.takeIf { it > 0 }?.let {
                getPluralString(
                    Res.plurals.reminder_expiring,
                    it,
                    it,
                )
            }
        val named =
            digest.expiring.takeIf { it.isNotEmpty() }?.let { items ->
                getPluralString(Res.plurals.reminder_expiring_named, items.size, names(items.map(nameOf)))
            }
        return ReminderText(
            title = getString(Res.string.reminder_title),
            body = listOfNotNull(named, expired).joinToString(separator = " · "),
            publicBody = listOfNotNull(counted, expired).joinToString(separator = " · "),
            channelName = getString(Res.string.reminder_channel),
        )
    }

    private suspend fun names(all: List<String>): String {
        val shown = all.take(NAMED)
        val rest = all.size - shown.size
        return when {
            rest > 0 -> getPluralString(Res.plurals.reminder_names_more, rest, shown.joinToString(), rest)
            shown.size == NAMED -> getString(Res.string.reminder_names_three, shown[0], shown[1], shown[2])
            shown.size == 2 -> getString(Res.string.reminder_names_two, shown[0], shown[1])
            else -> shown.single()
        }
    }
}
