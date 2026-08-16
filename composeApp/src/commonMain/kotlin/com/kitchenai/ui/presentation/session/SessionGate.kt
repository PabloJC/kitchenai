package com.kitchenai.ui.presentation.session

import androidx.compose.foundation.layout.WindowInsets
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

/**
 * [content] is composed only once the session exists. A screen built before that queries
 * `users/{uid}` without a uid and greets a new user with a permission error that fixes itself
 * a second later.
 */
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

    // These two are the only screens drawn before the shell exists, so nothing else is padding
    // them: the Scaffold that pads everything else lives inside `content`. Applied here rather
    // than inside the components, which are used under that Scaffold and would double up.
    val safe = modifier.windowInsetsPadding(WindowInsets.safeDrawing)

    when (val resolved = state) {
        SessionUiState.Loading -> LoadingState(safe)

        is SessionUiState.Failed ->
            ErrorState(
                message = resolved.message.resolve(),
                modifier = safe,
                retryLabel = retryLabel,
                onRetry = viewModel::retry,
            )

        is SessionUiState.Ready -> content(resolved.userId)
    }
}
