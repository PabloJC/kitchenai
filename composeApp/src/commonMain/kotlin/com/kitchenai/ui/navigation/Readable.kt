package com.kitchenai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitchenai.ui.designsystem.component.readableWidth

/** One destination in the readable column. Per destination, so Ideas can use the full width for two panes. */
@Composable
internal fun Readable(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().readableWidth()) { content() }
}
