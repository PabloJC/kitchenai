package com.kitchenai.admin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.kitchenai.admin.auth.adminSessions
import com.kitchenai.admin.auth.signInWithGoogle
import com.kitchenai.admin.auth.signOutOfAdmin
import com.kitchenai.admin.config.loadFirebaseConfig
import com.kitchenai.admin.firebase.initializeAdminFirebase
import kotlinx.coroutines.launch

@Composable
fun AdminApp() {
    var screen by remember { mutableStateOf<AdminScreen>(AdminScreen.Loading) }
    var signInError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loadFirebaseConfig().fold(
            onSuccess = { config ->
                initializeAdminFirebase(config)
                adminSessions().collect { session -> screen = screenFor(session) }
            },
            onFailure = { failure -> screen = AdminScreen.ConfigError(failure.message ?: "Could not load the config") },
        )
    }

    val signIn: () -> Unit = {
        signInError = null
        scope.launch {
            // A closed popup is the common failure: say so and leave the button usable.
            runCatching { signInWithGoogle() }.onFailure { failure -> signInError = failure.message }
        }
    }
    val signOut: () -> Unit = { scope.launch { signOutOfAdmin() } }

    when (val current = screen) {
        AdminScreen.Loading -> CenteredMessage("Loading…")
        is AdminScreen.ConfigError -> CenteredMessage(current.message)
        AdminScreen.SignIn -> SignInPage(error = signInError, onSignIn = signIn)
        is AdminScreen.NotAdmin -> NotAdminPage(email = current.email, onSignOut = signOut)
        is AdminScreen.Shell -> ShellPage(email = current.email, onSignOut = signOut)
    }
}
