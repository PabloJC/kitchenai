package com.kitchenai.ui.presentation.session

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.resolve
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.seconds

/**
 * [content] composes only once the session exists: earlier, it would read `users/{uid}` with no uid.
 * [onSettled] fires once the session is ready or has failed, so a platform splash can stand in for the spinner,
 * or after [SPLASH_CAP] at the latest: sign-in has no timeout, and a splash that never lifts looks like a crash.
 */
@Composable
fun SessionGate(
    defaultListName: String,
    retryLabel: String,
    modifier: Modifier = Modifier,
    onSettled: () -> Unit = {},
    viewModel: SessionViewModel = koinViewModel(),
    content: @Composable (UserId) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The composition guards the recomposition, the ViewModel guards the configuration change.
    LaunchedEffect(Unit) { viewModel.start(platformLanguageTags(), defaultListName) }
    val settled = state != SessionUiState.Loading
    // Keyed on settled: settling cancels the pending cap, so a quick session reports once.
    LaunchedEffect(settled) {
        if (!settled) delay(SPLASH_CAP)
        onSettled()
    }

    // Drawn before the shell exists, so the Scaffold inside `content` pads neither; the components
    // themselves must not, or they double up. fillMaxSize bounds the height ErrorState centres in.
    val safe = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)

    when (val resolved = state) {
        SessionUiState.Loading -> LoadingState(safe)

        is SessionUiState.Failure ->
            ErrorState(
                message = resolved.message.resolve(),
                modifier = safe,
                retryLabel = retryLabel,
                onRetry = viewModel::retry,
            )

        is SessionUiState.Ready -> content(resolved.userId)
    }
}

private val SPLASH_CAP = 3.seconds
