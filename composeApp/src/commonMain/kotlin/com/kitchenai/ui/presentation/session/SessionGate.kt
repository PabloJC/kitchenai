package com.kitchenai.ui.presentation.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.ErrorState
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.platform.platformLanguageTags
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

    when (val resolved = state) {
        SessionUiState.Loading -> LoadingState(modifier)

        is SessionUiState.Failed ->
            ErrorState(
                message = resolved.message,
                modifier = modifier,
                retryLabel = retryLabel,
                onRetry = viewModel::retry,
            )

        is SessionUiState.Ready -> content(resolved.userId)
    }
}
