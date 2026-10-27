package com.kitchenai.ui.platform

import androidx.compose.runtime.Composable

/** Redraws the home-screen widgets when the app goes to the background, with whatever changed in it. */
@Composable
expect fun HomeScreenWidgetsSync()
