package com.kitchenai.ui.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Centres the content and caps it near 80 characters of body text, so a tablet reads like a page
 * rather than a banner. Below the cap, every phone in portrait, it changes nothing.
 */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = READABLE_WIDTH)

private val READABLE_WIDTH = 640.dp
