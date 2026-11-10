package com.kitchenai.admin.ui

import com.kitchenai.admin.auth.AdminSession

/** What the admin renders. Only [Shell] mounts pages that read Firestore. */
sealed interface AdminScreen {
    data object Loading : AdminScreen

    data class ConfigError(
        val message: String,
    ) : AdminScreen

    data object SignIn : AdminScreen

    data class NotAdmin(
        val email: String?,
    ) : AdminScreen

    data class Shell(
        val email: String?,
    ) : AdminScreen
}

fun screenFor(session: AdminSession): AdminScreen =
    when (session) {
        AdminSession.Loading -> AdminScreen.Loading
        AdminSession.SignedOut -> AdminScreen.SignIn
        is AdminSession.NotAdmin -> AdminScreen.NotAdmin(session.email)
        is AdminSession.Admin -> AdminScreen.Shell(session.email)
    }
