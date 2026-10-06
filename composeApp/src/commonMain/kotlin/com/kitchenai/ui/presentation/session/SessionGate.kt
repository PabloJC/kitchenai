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
import org.koin.compose.viewmodel.koinViewModel

/** [content] composes only once the session exists: earlier, it would read `users/{uid}` with no uid. */
@Composable
fun SessionGate(
    defaultListName: String,
    retryLabel: String,
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = koinViewModel(),
    content: @Composable (UserId) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The composition guards the recomposition, the ViewModel guards the configuration change.
    LaunchedEffect(Unit) { viewModel.start(platformLanguageTags(), defaultListName) }

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
