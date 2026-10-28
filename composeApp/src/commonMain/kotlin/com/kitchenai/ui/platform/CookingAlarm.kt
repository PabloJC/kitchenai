package com.kitchenai.ui.platform

import kotlin.time.Instant

/** One timer's system alarm, its words already resolved: the alarm fires with no app to resolve them. */
data class CookingAlarm(
    val id: String,
    val fireAt: Instant,
    val title: String,
    val body: String,
    val channelName: String,
    val stopLabel: String,
)
