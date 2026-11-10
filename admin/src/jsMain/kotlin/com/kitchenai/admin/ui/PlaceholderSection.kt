package com.kitchenai.admin.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

@Composable
fun PlaceholderSection(section: AdminSection) {
    Section {
        H1 { Text(section.title) }
        P { Text("Not built yet (#${section.issue}).") }
    }
}
