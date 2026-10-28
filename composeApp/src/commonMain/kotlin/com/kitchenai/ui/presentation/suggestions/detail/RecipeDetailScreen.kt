package com.kitchenai.ui.presentation.suggestions.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitchenai.shared.domain.model.PantryItemId
import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.designsystem.component.ErrorBanner
import com.kitchenai.ui.designsystem.component.LoadingState
import com.kitchenai.ui.designsystem.component.RecipeImage
import com.kitchenai.ui.designsystem.component.Tag
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.motion.sharedRecipeImage
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.designsystem.theme.PillShape
import com.kitchenai.ui.navigation.DetailTopBarState
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.platform.rememberShareText
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.resolve
import com.kitchenai.ui.presentation.common.text
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.detail_add_missing
import com.kitchenai.ui.resources.detail_candidate_prompt
import com.kitchenai.ui.resources.detail_cook
import com.kitchenai.ui.resources.detail_cook_anyway
import com.kitchenai.ui.resources.detail_cook_missing_hint
import com.kitchenai.ui.resources.detail_have
import com.kitchenai.ui.resources.detail_household_avoids
import com.kitchenai.ui.resources.detail_in_pantry
import com.kitchenai.ui.resources.detail_ingredients
import com.kitchenai.ui.resources.detail_missing
import com.kitchenai.ui.resources.detail_optional_suffix
import com.kitchenai.ui.resources.detail_servings
import com.kitchenai.ui.resources.detail_servings_count
import com.kitchenai.ui.resources.detail_servings_decrease
import com.kitchenai.ui.resources.detail_servings_increase
import com.kitchenai.ui.resources.detail_start_cooking
import com.kitchenai.ui.resources.detail_step_spoken
import com.kitchenai.ui.resources.detail_steps
import com.kitchenai.ui.resources.detail_undo
import com.kitchenai.ui.resources.detail_unverifiable
import com.kitchenai.ui.resources.detail_unverifiable_body
import com.kitchenai.ui.resources.detail_view_list
import com.kitchenai.ui.resources.error_retry
import com.kitchenai.ui.resources.shopping_default_list
import com.kitchenai.ui.resources.snack_added_count
import com.kitchenai.ui.resources.snack_cook_undone
import com.kitchenai.ui.resources.snack_cooked
import com.kitchenai.ui.resources.snack_counts_joined
import com.kitchenai.ui.resources.snack_not_needed_count
import com.kitchenai.ui.resources.snack_saved
import com.kitchenai.ui.resources.snack_unsaved
import com.kitchenai.ui.resources.suggestions_minutes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * [detailTopBar] null means embedded beside the Ideas list on a wide window: the shell's bar stays
 * up, and the photograph does not travel, since its card is on screen beside it.
 */
@Composable
fun RecipeDetailScreen(
    userId: UserId,
    recipeId: RecipeId,
    detailTopBar: DetailTopBarState?,
    routes: RecipeDetailRoutes,
    modifier: Modifier = Modifier,
    viewModel: RecipeDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scroll = rememberScrollState()
    var imageHeight by remember { mutableIntStateOf(0) }
    val barHeight = with(LocalDensity.current) { Dimens.topBar.roundToPx() }

    // Nothing to show but the failure: the header and the cards would draw an empty dish around it.
    val loadFailed = state.error != null && state.title.isEmpty()
    // Solid once content scrolls under the bar; titled once the header's own title has scrolled away.
    val solid by remember { derivedStateOf { imageHeight == 0 || scroll.value >= imageHeight - barHeight } }
    val titled by remember { derivedStateOf { imageHeight == 0 || scroll.value >= imageHeight } }
    DetailEffects(userId, recipeId, viewModel, snackbar, routes.shoppingList)
    detailTopBar?.let { HideSharedTopBar(it) }

    state.cookReview?.let { review ->
        CookReviewDialog(
            review,
            state.cooksAnyway,
            onConfirm = viewModel::cook,
            onDismiss = viewModel::dismissCookReview,
        )
    }

    // The insets are AppShell's; a second round here would open a gap between the toolbar and
    // the bottom bar rather than closing one.
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RecipeDetailTopBar(
                solid || loadFailed,
                titled || loadFailed,
                state,
                routes.back,
                viewModel::toggleSaved,
                rememberShare(state),
            )
        },
        // Pinned rather than the scroll's last row, so both actions are reachable without
        // scrolling past the steps.
        bottomBar = { if (!state.isLoading && !loadFailed) Actions(state, viewModel) },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        // No top padding once there is a photo: it is what runs under the transparent bar.
        val top = if (loadFailed) padding.calculateTopPadding() else Dimens.none
        DetailBody(
            sharedImageKey = recipeId.value.takeIf { detailTopBar != null },
            state = state,
            viewModel = viewModel,
            onStartCooking = routes.startCooking,
            onImageHeight = { height -> imageHeight = height },
            modifier = Modifier.padding(top = top, bottom = padding.calculateBottomPadding()).verticalScroll(scroll),
        )
    }
}

@Composable
private fun DetailBody(
    sharedImageKey: String?,
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
    onStartCooking: () -> Unit,
    onImageHeight: (Int) -> Unit,
    modifier: Modifier,
) {
    // Nothing to show but the failure: the header and the cards would draw an empty dish around it.
    val loadFailed = state.error != null && state.title.isEmpty()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.large),
    ) {
        if (!loadFailed) Header(sharedImageKey, state, viewModel, onImageHeight)
        state.error?.let { message ->
            ErrorBanner(
                message = message.resolve(),
                modifier =
                    Modifier.padding(
                        start = Dimens.large,
                        top = if (loadFailed) Dimens.large else Dimens.none,
                        end = Dimens.large,
                    ),
                retryLabel = stringResource(Res.string.error_retry),
                onRetry = viewModel::retry,
            )
        }
        if (state.avoidedByHousehold.isNotEmpty()) {
            ErrorBanner(
                message = stringResource(Res.string.detail_household_avoids, state.avoidedByHousehold.joinToString()),
                modifier = Modifier.padding(horizontal = Dimens.large),
            )
        }
        if (!loadFailed) {
            IngredientsCard(state)
            if (state.unverifiable.isNotEmpty()) {
                UnverifiableCard(state.unverifiable, viewModel::confirmCandidate)
            }
            StepsCard(state.steps, onStartCooking)
        }
    }
}

@Composable
private fun DetailEffects(
    userId: UserId,
    recipeId: RecipeId,
    viewModel: RecipeDetailViewModel,
    snackbar: SnackbarHostState,
    onViewShoppingList: () -> Unit,
) {
    // The same name the shopping screen would create the list under, read here because a
    // LaunchedEffect block is a coroutine and not composition.
    val defaultListName = stringResource(Res.string.shopping_default_list)
    LaunchedEffect(userId, recipeId) {
        viewModel.start(userId, recipeId, platformLanguageTags(), defaultListName)
    }
    LaunchedEffect(viewModel) {
        // Launched apart: a snackbar with an action suspends until dismissed, and must not hold back the next.
        viewModel.events.collect { event -> launch { snackbar.announce(event, viewModel, onViewShoppingList) } }
    }
}

/**
 * This screen draws its own bar over the photo, so it asks AppShell for none, and hands the
 * shared one back once it is no longer the screen showing.
 */
@Composable
private fun HideSharedTopBar(detailTopBar: DetailTopBarState) {
    val owner = remember { Any() }
    DisposableEffect(Unit) {
        detailTopBar.claim(owner, title = null, hidden = true)
        onDispose { detailTopBar.release(owner) }
    }
}

/** Null until there is a recipe to share, so the action never offers an empty message. */
@Composable
private fun rememberShare(state: RecipeDetailUiState): ((anchor: Rect?) -> Unit)? {
    val share = rememberShareText()
    if (state.title.isEmpty()) return null
    val labels =
        ShareLabels(
            servings = pluralStringResource(Res.plurals.detail_servings_count, state.servings, state.servings),
            minutes = state.totalMinutes?.let { stringResource(Res.string.suggestions_minutes, it) },
            ingredients = stringResource(Res.string.detail_ingredients),
            steps = stringResource(Res.string.detail_steps),
            optionalSuffix = stringResource(Res.string.detail_optional_suffix),
        )
    return { anchor -> share(state.shareText(labels), anchor) }
}

@Composable
private fun Header(
    sharedImageKey: String?,
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
    onImageHeight: (Int) -> Unit,
) {
    // Edge to edge and under the bar, unlike everything below it: the photograph sits flush with
    // the top of the screen rather than margined like body content.
    // The card's photograph lands here: the same recipe id names both ends of the move.
    val shared = sharedImageKey?.let { key -> Modifier.sharedRecipeImage(key) } ?: Modifier
    RecipeImage(tags = state.tagRefs, modifier = shared.onSizeChanged { onImageHeight(it.height) })
    Column(
        modifier = Modifier.padding(horizontal = Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        Text(state.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        state.summary?.let { summary -> Text(summary, style = MaterialTheme.typography.bodyMedium) }
        MetadataRow(state)
        if (state.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.extraSmall)) {
                state.tags.forEach { tag ->
                    Tag(
                        label = tag,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        ServingsStepper(state, viewModel)
    }
}

/** Minutes and how much of the dish the pantry already holds, each with an icon, read at a glance. */
@Composable
private fun MetadataRow(state: RecipeDetailUiState) {
    val required = state.held.count { !it.optional } + state.missing.count { !it.optional }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.large),
        verticalArrangement = Arrangement.spacedBy(Dimens.extraSmall),
    ) {
        state.totalMinutes?.let { minutes ->
            MetadataItem(Icons.Outlined.Schedule, stringResource(Res.string.suggestions_minutes, minutes))
        }
        if (required > 0) {
            val held = state.held.count { !it.optional }
            MetadataItem(Icons.Outlined.Kitchen, stringResource(Res.string.detail_in_pantry, held, required))
        }
    }
}

@Composable
private fun MetadataItem(
    icon: ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.extraSmall),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One rounded group rather than two loose buttons either side of the count. */
@Composable
private fun ServingsStepper(
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = PillShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.small, vertical = Dimens.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            Text(stringResource(Res.string.detail_servings), style = MaterialTheme.typography.labelSmall)
            StepperButton(
                Icons.Outlined.Remove,
                stringResource(Res.string.detail_servings_decrease),
                enabled = state.servings > 1 && !state.isWorking,
            ) { viewModel.setServings(state.servings - 1) }
            val servingsSpoken = pluralStringResource(Res.plurals.detail_servings_count, state.servings, state.servings)
            Text(
                state.servings.toString(),
                style = MaterialTheme.typography.titleMedium,
                // Read as "4 servings", and re-read when a tap changes it.
                modifier =
                    Modifier.semantics {
                        contentDescription = servingsSpoken
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            StepperButton(
                Icons.Outlined.Add,
                stringResource(Res.string.detail_servings_increase),
                enabled = !state.isWorking,
            ) { viewModel.setServings(state.servings + 1) }
        }
    }
}

/** An icon button rather than a drawn "-" / "+": it carries the button role and a spoken name. */
@Composable
private fun StepperButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // IconButton keeps the 48dp touch target: this is the button someone taps repeatedly while
    // their hands are full of food.
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        enabled = enabled,
    ) { Icon(icon, contentDescription = label) }
}

@Composable
private fun IngredientsCard(state: RecipeDetailUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        Column(
            modifier = Modifier.padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            Text(stringResource(Res.string.detail_ingredients), style = MaterialTheme.typography.titleMedium)
            // One list, not two: a section header per bucket read like a shopping list. The
            // status now travels with each row instead, as a chip.
            state.held.forEach { line -> StatusRow(line, held = true) }
            state.missing.forEach { line -> StatusRow(line, held = false) }
        }
    }
}

@Composable
private fun StatusRow(
    line: IngredientLineUi,
    held: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.extraSmall)) {
            line.quantity?.let { amount ->
                Text(amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(line.name + if (line.optional) stringResource(Res.string.detail_optional_suffix) else "")
        }
        val colors = MaterialTheme.colorScheme
        Tag(
            label = stringResource(if (held) Res.string.detail_have else Res.string.detail_missing),
            containerColor = if (held) colors.secondaryContainer else colors.errorContainer,
            contentColor = if (held) colors.onSecondaryContainer else colors.onErrorContainer,
        )
    }
}

@Composable
private fun UnverifiableCard(
    lines: List<IngredientLineUi>,
    onConfirmCandidate: (PantryItemId) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        Column(
            modifier = Modifier.padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            // Its own section with its own sentence: this is not "missing", and a reader who
            // took it for missing would go shopping for something they may already own.
            Text(stringResource(Res.string.detail_unverifiable), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.detail_unverifiable_body), style = MaterialTheme.typography.bodySmall)
            lines.forEach { line ->
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.extraSmall)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(line.name + if (line.optional) stringResource(Res.string.detail_optional_suffix) else "")
                        line.quantity?.let { amount -> Text(amount, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (line.candidates.isNotEmpty()) CandidateRow(line.candidates, onConfirmCandidate)
                }
            }
        }
    }
}

/**
 * A candidate offered, never assumed: tapping one only toggles this chip. PantryMatcher's own
 * verdict for the line above does not change either way (#163).
 */
@Composable
private fun CandidateRow(
    candidates: List<CandidateUi>,
    onConfirm: (PantryItemId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.extraSmall)) {
        Text(stringResource(Res.string.detail_candidate_prompt), style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.extraSmall)) {
            candidates.forEach { candidate ->
                TermChip(
                    label = candidate.label,
                    selected = candidate.confirmed,
                    onToggle = { onConfirm(candidate.id) },
                )
            }
        }
    }
}

@Composable
private fun StepsCard(
    steps: List<String>,
    onStartCooking: () -> Unit,
) {
    if (steps.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.large)) {
        Column(
            modifier = Modifier.padding(Dimens.large),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.detail_steps),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onStartCooking, modifier = Modifier.heightIn(min = Dimens.touchTarget)) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(Dimens.small))
                    Text(stringResource(Res.string.detail_start_cooking))
                }
            }
            val ruleColor = MaterialTheme.colorScheme.outlineVariant
            Column(
                // One rule running the length of the list, not a segment per row: drawn behind
                // the circles once this column's own height is known, rather than guessed at
                // row by row against text of unknown length.
                modifier =
                    Modifier.drawBehind {
                        if (steps.size < 2) return@drawBehind
                        val x = Dimens.huge.toPx() / 2
                        val inset = Dimens.huge.toPx() / 2
                        drawLine(
                            color = ruleColor,
                            start = Offset(x, inset),
                            end = Offset(x, size.height - inset),
                            strokeWidth = DividerDefaults.Thickness.toPx(),
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(Dimens.medium),
            ) {
                steps.forEachIndexed { index, step -> StepRow(index + 1, steps.size, step, isFirst = index == 0) }
            }
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    total: Int,
    step: String,
    isFirst: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val fill = if (isFirst) colors.primaryContainer else colors.surfaceContainerHighest
    val onFill = if (isFirst) colors.onPrimaryContainer else colors.onSurfaceVariant
    var circle = Modifier.size(Dimens.huge).clip(CircleShape).background(fill)
    if (!isFirst) circle = circle.border(DividerDefaults.Thickness, colors.outlineVariant, CircleShape)
    val spoken = stringResource(Res.string.detail_step_spoken, number, total, step)
    // One node per step: the number in its circle and the text would otherwise be read apart.
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.medium),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Box(modifier = circle, contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.titleMedium, color = onFill)
        }
        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Dimens.small))
    }
}

@Composable
private fun Actions(
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        // heightIn rather than a fixed height: a label that needs two lines (Spanish's
        // "Añadir lo que falta" at half the row's width) grows the button instead of clipping,
        // and CenterVertically keeps it lined up with a neighbour that stayed one line.
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.large),
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = viewModel::addMissingToList,
                enabled = !state.isWorking && state.missing.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = Dimens.touchTarget),
            ) {
                Icon(
                    Icons.Outlined.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(Dimens.small))
                Text(stringResource(Res.string.detail_add_missing))
            }
            Button(
                onClick = viewModel::reviewCook,
                enabled = state.canCook,
                modifier = Modifier.weight(1f).heightIn(min = Dimens.touchTarget),
            ) {
                Icon(
                    Icons.Outlined.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(Dimens.small))
                Text(stringResource(if (state.cooksAnyway) Res.string.detail_cook_anyway else Res.string.detail_cook))
            }
        }
        state.cookHint()?.let { hint ->
            Text(
                text = hint.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Dimens.large, end = Dimens.large, bottom = Dimens.medium),
            )
        }
    }
}

/**
 * What Cook anyway leaves out, before it is tapped. A busy or still loading screen is transient
 * and says nothing: a hint that flickered per tap would be noise. Optional lines do not count.
 */
internal fun RecipeDetailUiState.cookHint(): UiText? {
    val settled = !isLoading && !isWorking
    val required = missing.count { !it.optional }
    if (!settled || required == 0) return null
    return UiText.Plural(Res.plurals.detail_cook_missing_hint, required)
}

private suspend fun SnackbarHostState.announce(
    event: RecipeDetailEvent,
    viewModel: RecipeDetailViewModel,
    onViewShoppingList: () -> Unit,
) {
    // Bounded: with an action, Material's default is Indefinite and the snackbar would never go away.
    val action: Pair<String?, (() -> Unit)?> =
        when (event) {
            RecipeDetailEvent.Unsaved -> getString(Res.string.detail_undo) to viewModel::save
            is RecipeDetailEvent.Cooked ->
                if (event.cooked.deductions.isEmpty()) {
                    null to null
                } else {
                    getString(Res.string.detail_undo) to {
                        viewModel.undoCook(event.cooked)
                    }
                }
            is RecipeDetailEvent.AddedToList ->
                if (event.added == 0) null to null else getString(Res.string.detail_view_list) to onViewShoppingList
            else -> null to null
        }
    val (label, onAction) = action
    val outcome =
        showSnackbar(
            message = event.sentence().text(),
            actionLabel = label,
            duration = if (label == null) SnackbarDuration.Short else SnackbarDuration.Long,
        )
    if (outcome == SnackbarResult.ActionPerformed) onAction?.invoke()
}

/**
 * Separate from [announce] so the wording can be tested: there is no Compose test harness here,
 * and the sentence is the part that can be wrong.
 */
internal fun RecipeDetailEvent.sentence(): UiText =
    when (this) {
        // Both counts, each agreeing with its own noun. "Skipped" is a line the pantry covers or
        // one the recipe marks optional, never one already on the list.
        is RecipeDetailEvent.AddedToList ->
            UiText.Joined(
                Res.string.snack_counts_joined,
                listOf(
                    UiText.Plural(Res.plurals.snack_added_count, added),
                    UiText.Plural(Res.plurals.snack_not_needed_count, skipped),
                ),
            )
        is RecipeDetailEvent.Cooked -> UiText.of(Res.string.snack_cooked)
        RecipeDetailEvent.CookUndone -> UiText.of(Res.string.snack_cook_undone)
        RecipeDetailEvent.Saved -> UiText.of(Res.string.snack_saved)
        RecipeDetailEvent.Unsaved -> UiText.of(Res.string.snack_unsaved)
        is RecipeDetailEvent.Failed -> message
    }
