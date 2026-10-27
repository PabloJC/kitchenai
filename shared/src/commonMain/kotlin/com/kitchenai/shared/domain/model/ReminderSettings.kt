package com.kitchenai.shared.domain.model

/**
 * This device's expiry reminder: whether it is on, the local time of day it fires, and whether the
 * person has already been asked. Asked once, in context, and never again whatever they answered.
 */
data class ReminderSettings(
    val enabled: Boolean = true,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = 0,
    val offered: Boolean = false,
) {
    companion object {
        const val DEFAULT_HOUR = 18
    }
}
