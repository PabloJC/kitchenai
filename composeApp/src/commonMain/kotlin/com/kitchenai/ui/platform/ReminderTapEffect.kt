package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/** Runs [onOpen] when the app was opened, or brought forward, by tapping an expiry reminder. */
@Composable
expect fun ReminderTapEffect(onOpen: () -> Unit)
