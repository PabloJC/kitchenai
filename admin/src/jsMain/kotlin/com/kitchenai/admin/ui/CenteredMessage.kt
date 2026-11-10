package com.kitchenai.admin.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun CenteredMessage(message: String) {
    Main({ classes("centered") }) {
        P { Text(message) }
    }
}
