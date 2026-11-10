package com.kitchenai.admin

import com.kitchenai.admin.ui.AdminApp
import org.jetbrains.compose.web.renderComposable

fun main() {
    renderComposable(rootElementId = "root") { AdminApp() }
}
