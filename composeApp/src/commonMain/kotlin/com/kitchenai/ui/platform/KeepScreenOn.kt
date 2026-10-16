package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/** Keeps the display awake while this call is composed, and gives the normal timeout back once it leaves. */
@Composable
expect fun KeepScreenOn()
