package com.kitchenai.ui.presentation.suggestions.cooking

/** What a timer's notification says, resolved by the screen that starts it. */
data class CookingAlarmText(
    val title: String,
    val body: String,
    val channelName: String,
    val stopLabel: String,
)
