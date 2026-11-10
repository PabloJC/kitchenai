package com.kitchenai.admin.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun NotAdminPage(
    email: String?,
    onSignOut: () -> Unit,
) {
    Main({ classes("centered") }) {
        H1 { Text("Not an admin") }
        P {
            Text("${email ?: "This account"} has no admin access. Ask for it with ")
            Code { Text("tools/grant-admin.mjs") }
            Text(", then sign in again.")
        }
        Button({ onClick { onSignOut() } }) { Text("Sign out") }
    }
}
