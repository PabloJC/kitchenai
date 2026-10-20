package com.kitchenai.ui.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.platform.platformLanguageTags
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows the first run in place of [content] until it is over. [content] gets where it should open
 * once, and calls the second argument when it has gone there, so a recomposition does not go again.
 */
@Composable
fun OnboardingGate(
    userId: UserId,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
    content: @Composable (landing: OnboardingExit?, onLanded: () -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var landing by remember { mutableStateOf<OnboardingExit?>(null) }

    LaunchedEffect(userId) { viewModel.start(userId, platformLanguageTags()) }
    LaunchedEffect(viewModel) { viewModel.exits.collect { exit -> landing = exit } }

    when (state.status) {
        OnboardingStatus.CHECKING -> LoadingState(modifier)
        OnboardingStatus.SHOWING -> OnboardingScreen(state, viewModel, modifier)
        OnboardingStatus.DONE -> content(landing) { landing = null }
    }
}
