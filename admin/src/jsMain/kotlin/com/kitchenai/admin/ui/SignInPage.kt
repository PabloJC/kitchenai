package com.kitchenai.admin.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun SignInPage(
    error: String?,
    onSignIn: () -> Unit,
) {
    Main({ classes("centered") }) {
        H1 { Text("KitchenAI admin") }
        Button({ onClick { onSignIn() } }) { Text("Sign in with Google") }
        error?.let { message -> P({ classes("error") }) { Text(message) } }
    }
}
