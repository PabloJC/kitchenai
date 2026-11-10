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
import com.kitchenai.admin.core.catchingFailures
import com.kitchenai.admin.di.adminModule
import com.kitchenai.admin.firebase.initializeAdminFirebase
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.dsl.koinApplication

@Composable
fun AdminApp() {
    var screen by remember { mutableStateOf<AdminScreen>(AdminScreen.Loading) }
    // The last sign-in or sign-out failure, shown on whichever page offered the button.
    var authError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var koin by remember { mutableStateOf<Koin?>(null) }

    LaunchedEffect(Unit) {
        loadFirebaseConfig().fold(
            onSuccess = { config ->
                initializeAdminFirebase(config)
                koin = koinApplication { modules(adminModule(config)) }.koin
                adminSessions().collect { session -> screen = screenFor(session) }
            },
            onFailure = { failure -> screen = AdminScreen.ConfigError(failure.message ?: "Could not load the config") },
        )
    }

    val signIn: () -> Unit = {
        authError = null
        scope.launch {
            // A closed popup is the common failure: say so and leave the button usable.
            catchingFailures { signInWithGoogle() }.onFailure { failure -> authError = failure.message }
        }
    }
    val signOut: () -> Unit = {
        authError = null
        scope.launch {
            catchingFailures { signOutOfAdmin() }
                .onFailure { failure -> authError = "Could not sign out: ${failure.message}" }
        }
    }

    when (val current = screen) {
        AdminScreen.Loading -> CenteredMessage("Loading…")
        is AdminScreen.ConfigError -> CenteredMessage(current.message)
        AdminScreen.SignIn -> SignInPage(error = authError, onSignIn = signIn)
        is AdminScreen.NotAdmin -> NotAdminPage(email = current.email, error = authError, onSignOut = signOut)
        // A session exists only once Firebase, and with it the graph, is up.
        is AdminScreen.Shell -> koin?.let { graph -> ShellPage(current.email, authError, graph, onSignOut = signOut) }
    }
}
