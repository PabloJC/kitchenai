package com.kitchenai.ui.presentation.pantry

import com.kitchenai.shared.domain.agent.SuggestionOptions
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_expired_days_ago
import com.kitchenai.ui.resources.pantry_expired_yesterday
import com.kitchenai.ui.resources.pantry_expires_in_days
import com.kitchenai.ui.resources.pantry_expires_today
import com.kitchenai.ui.resources.pantry_expires_tomorrow

/** Today, tomorrow and the day after count as soon: the same number the agent is told. */
internal const val EXPIRING_SOON_DAYS = SuggestionOptions.DEFAULT_EXPIRING_SOON_DAYS

internal fun expiryLabel(daysLeft: Int): UiText =
    when (daysLeft) {
        0 -> UiText.of(Res.string.pantry_expires_today)
        1 -> UiText.of(Res.string.pantry_expires_tomorrow)
        -1 -> UiText.of(Res.string.pantry_expired_yesterday)
        in 2..Int.MAX_VALUE -> UiText.Plural(Res.plurals.pantry_expires_in_days, daysLeft)
        else -> UiText.Plural(Res.plurals.pantry_expired_days_ago, -daysLeft)
    }
