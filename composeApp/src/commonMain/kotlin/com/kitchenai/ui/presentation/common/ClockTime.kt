package com.kitchenai.ui.presentation.common

/** 24-hour, as the reminder offer, the Profile row and the picker all show it. */
fun clockTime(
    hour: Int,
    minute: Int,
): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
