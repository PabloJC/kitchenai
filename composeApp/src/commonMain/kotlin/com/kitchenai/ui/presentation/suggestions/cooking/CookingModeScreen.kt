package com.kitchenai.ui.presentation.suggestions.cooking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.EmptyState
import com.kitchenai.ui.designsystem.component.ErrorBanner
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.navigation.DetailTopBarState
import com.kitchenai.ui.platform.KeepScreenOn
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailUiState
import com.kitchenai.ui.presentation.suggestions.detail.RecipeDetailViewModel
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.cooking_done
import com.kitchenai.ui.resources.cooking_ingredients
import com.kitchenai.ui.resources.cooking_ingredients_ticked
import com.kitchenai.ui.resources.cooking_next
import com.kitchenai.ui.resources.cooking_no_steps
import com.kitchenai.ui.resources.cooking_previous
import com.kitchenai.ui.resources.cooking_start_timer
import com.kitchenai.ui.resources.cooking_step_counter
import com.kitchenai.ui.resources.cooking_timer_dismiss
import com.kitchenai.ui.resources.cooking_timer_done
import com.kitchenai.ui.resources.cooking_timer_running
import com.kitchenai.ui.resources.cooking_timer_stop
import com.kitchenai.ui.resources.detail_steps
import com.kitchenai.ui.resources.error_retry
import com.kitchenai.ui.resources.shopping_default_list
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration

/**
 * One step per page, in large type, with the controls at the bottom where a thumb reaches them.
 * [detail] is the recipe detail's own ViewModel, so the servings chosen there are the ones the
 * ingredients are measured in here, and finishing cooks through the same confirmation.
 */
@Composable
fun CookingModeScreen(
    userId: UserId,
    recipeId: RecipeId,
    detailTopBar: DetailTopBarState,
    onExit: () -> Unit,
    detail: RecipeDetailViewModel,
    modifier: Modifier = Modifier,
    viewModel: CookingModeViewModel = koinViewModel(),
) {
    KeepScreenOn()
    val recipe by detail.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showingIngredients by rememberSaveable { mutableStateOf(false) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    val lines = remember(recipe) { recipe.held + recipe.missing + recipe.unverifiable }
    val pager = rememberPagerState { recipe.steps.size }
    CookingEffects(userId, recipeId, detail, viewModel, snackbar)
    PublishTitle(recipe.title, detailTopBar)

    if (finishing) {
        // Both answers leave; the dialog's own state goes with the popped entry.
        FinishDialog(
            onSubtract = {
                detail.cook()
                onExit()
            },
            onSkip = onExit,
            onDismiss = { finishing = false },
        )
    }
    if (showingIngredients) {
        IngredientsSheet(lines, state.ticked, viewModel::toggleIngredient, onDismiss = { showingIngredients = false })
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (recipe.steps.isNotEmpty()) {
                // Without every required line the pantry cannot be subtracted from, so Done just leaves.
                StepNavigation(pager, onDone = { if (recipe.canCook) finishing = true else onExit() })
            }
        },
    ) { padding ->
        when {
            recipe.isLoading -> LoadingState(Modifier.padding(padding))
            recipe.steps.isEmpty() -> NothingToFollow(recipe, detail, Modifier.padding(padding))
            else ->
                Steps(
                    recipe = recipe,
                    state = state,
                    pager = pager,
                    lineCount = lines.size,
                    viewModel = viewModel,
                    onIngredients = { showingIngredients = true },
                    modifier = Modifier.padding(padding),
                )
        }
    }
}

@Composable
private fun Steps(
    recipe: RecipeDetailUiState,
    state: CookingModeUiState,
    pager: PagerState,
    lineCount: Int,
    viewModel: CookingModeViewModel,
    onIngredients: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.timer?.let { timer -> TimerBar(timer, viewModel::dismissTimer) }
        Progress(
            step = pager.currentPage + 1,
            steps = recipe.steps.size,
            ticked = state.ticked.count { it < lineCount },
            lines = lineCount,
            onIngredients = onIngredients,
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            StepPage(recipe.steps[page]) { duration -> viewModel.startTimer(page + 1, duration) }
        }
    }
}

@Composable
private fun CookingEffects(
    userId: UserId,
    recipeId: RecipeId,
    detail: RecipeDetailViewModel,
    viewModel: CookingModeViewModel,
    snackbar: SnackbarHostState,
) {
    val haptics = LocalHapticFeedback.current
    val defaultListName = stringResource(Res.string.shopping_default_list)
    // Idempotent: a no-op unless the process was restored straight into this screen.
    LaunchedEffect(userId, recipeId) { detail.start(userId, recipeId, platformLanguageTags(), defaultListName) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { done ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            // Launched: the snackbar suspends for its whole duration, and the next buzz must not wait on it.
            launch {
                snackbar.showSnackbar(getString(Res.string.cooking_timer_done, done.step), duration = SnackbarDuration.Long)
            }
        }
    }
}

/** The same ownership handshake recipe detail uses, so neither screen clears the other's title mid-transition. */
@Composable
private fun PublishTitle(
    title: String,
    detailTopBar: DetailTopBarState,
) {
    val owner = remember { Any() }
    DisposableEffect(title) {
        detailTopBar.claim(owner, title.ifBlank { null })
        onDispose {}
    }
    DisposableEffect(Unit) { onDispose { detailTopBar.release(owner) } }
}

@Composable
private fun NothingToFollow(
    recipe: RecipeDetailUiState,
    detail: RecipeDetailViewModel,
    modifier: Modifier,
) {
    val error = recipe.error
    if (error != null) {
        ErrorBanner(
            message = error.resolve(),
            modifier = modifier.padding(Dimens.large),
            retryLabel = stringResource(Res.string.error_retry),
            onRetry = detail::retry,
        )
    } else {
        EmptyState(
            title = stringResource(Res.string.detail_steps),
            body = stringResource(Res.string.cooking_no_steps),
            modifier = modifier,
        )
    }
}

@Composable
private fun Progress(
    step: Int,
    steps: Int,
    ticked: Int,
    lines: Int,
    onIngredients: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large, vertical = Dimens.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.cooking_step_counter, step, steps),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (lines > 0) {
            TextButton(onClick = onIngredients, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
                Icon(Icons.Outlined.Checklist, contentDescription = null)
                Spacer(Modifier.width(Dimens.small))
                Column {
                    Text(stringResource(Res.string.cooking_ingredients))
                    Text(
                        stringResource(Res.string.cooking_ingredients_ticked, ticked, lines),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepPage(
    step: String,
    onTimer: (Duration) -> Unit,
) {
    val duration = remember(step) { firstDurationIn(step) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.extraLarge),
        verticalArrangement = Arrangement.spacedBy(Dimens.extraLarge),
    ) {
        Text(step, style = MaterialTheme.typography.headlineSmall)
        if (duration != null) {
            AssistChip(
                onClick = { onTimer(duration) },
                label = { Text(stringResource(Res.string.cooking_start_timer, duration.toClock())) },
                leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                modifier = Modifier.heightIn(min = Dimens.touchTarget),
            )
        }
    }
}

@Composable
private fun TimerBar(
    timer: StepTimerUi,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (timer.isDone) colors.tertiaryContainer else colors.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.large, vertical = Dimens.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                val label = if (timer.isDone) Res.string.cooking_timer_done else Res.string.cooking_timer_running
                Text(stringResource(label, timer.step), style = MaterialTheme.typography.labelLarge)
                Text(timer.remaining.toClock(), style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
                Text(
                    stringResource(
                        if (timer.isDone) Res.string.cooking_timer_dismiss else Res.string.cooking_timer_stop,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StepNavigation(
    pager: PagerState,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == pager.pageCount - 1
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.large),
            horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            OutlinedButton(
                onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                enabled = page > 0,
                modifier = Modifier.weight(1f).heightIn(min = Dimens.cookingControl),
            ) { Text(stringResource(Res.string.cooking_previous), style = MaterialTheme.typography.titleMedium) }
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(page + 1) } },
                modifier = Modifier.weight(1f).heightIn(min = Dimens.cookingControl),
            ) {
                Text(
                    stringResource(if (last) Res.string.cooking_done else Res.string.cooking_next),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
