package com.kitchenai.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.Nav
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.w3c.dom.events.Event

@Composable
fun ShellPage(
    email: String?,
    onSignOut: () -> Unit,
) {
    // Hash routing: the page is one file on Hosting, and a hash never reaches the server.
    var section by remember { mutableStateOf(sectionFor(window.location.hash)) }
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { section = sectionFor(window.location.hash) }
        window.addEventListener("hashchange", listener)
        onDispose { window.removeEventListener("hashchange", listener) }
    }

    Div({ classes("shell") }) {
        Nav({ classes("shell-nav") }) {
            B { Text("KitchenAI admin") }
            Ul {
                ADMIN_SECTIONS.forEach { item ->
                    Li {
                        A(item.href(), { if (item == section) classes("active") }) { Text(item.title) }
                    }
                }
            }
            Div({ classes("shell-account") }) {
                Span { Text(email.orEmpty()) }
                Button({ onClick { onSignOut() } }) { Text("Sign out") }
            }
        }
        Main({ classes("shell-content") }) {
            PlaceholderSection(section)
        }
    }
}
