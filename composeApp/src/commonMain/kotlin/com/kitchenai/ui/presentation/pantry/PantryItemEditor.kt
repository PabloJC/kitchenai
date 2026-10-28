package com.kitchenai.ui.presentation.pantry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.designsystem.component.QuantityField
import com.kitchenai.ui.designsystem.component.QuantityFieldOptions
import com.kitchenai.ui.designsystem.component.TermChip
import com.kitchenai.ui.designsystem.format.decimalSeparatorFor
import com.kitchenai.ui.designsystem.format.formatAmount
import com.kitchenai.ui.designsystem.theme.Dimens
import com.kitchenai.ui.platform.platformLanguageTags
import com.kitchenai.ui.presentation.common.ExpiryField
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.pantry_amount
import com.kitchenai.ui.resources.pantry_delete
import com.kitchenai.ui.resources.pantry_editor_added
import com.kitchenai.ui.resources.pantry_editor_amount_malformed
import com.kitchenai.ui.resources.pantry_editor_clear_search
import com.kitchenai.ui.resources.pantry_editor_free_text_hint
import com.kitchenai.ui.resources.pantry_editor_recent
import com.kitchenai.ui.resources.pantry_editor_save_and_add
import com.kitchenai.ui.resources.pantry_editor_title_add
import com.kitchenai.ui.resources.pantry_editor_title_edit
import com.kitchenai.ui.resources.pantry_editor_unit
import com.kitchenai.ui.resources.pantry_save
import com.kitchenai.ui.resources.pantry_search
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * The add and edit sheet. It holds the draft locally and reports it once, so a half-typed amount
 * never reaches a use case.
 *
 * The unit selector offers the vocabulary the catalogue measures ingredients in and the chips the
 * one the pantry already stores them in; neither list is written down here. Typing a line the
 * search does not find is not blocked: it becomes free text, the way the shopping field's does.
 * A blank amount is one, in the ingredient's own unit, so salt costs a pick and a tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryItemEditor(
    state: PantryUiState,
    onDismiss: () -> Unit,
    onSubmit: (PantryItemDraft) -> Unit,
    onSubmitAndAddAnother: (PantryItemDraft) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = rememberEditorForm(state.editing, state.editorRevision, state.prefill)

    // Fully expanded from the start and scrollable: partially expanded, Save sat below the fold.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.large)
                    .padding(bottom = Dimens.extraLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.medium),
        ) {
            EditorFields(state, form, onSubmit, onSubmitAndAddAnother, onDelete)
        }
    }
}

@Composable
private fun EditorFields(
    state: PantryUiState,
    form: EditorForm,
    onSubmit: (PantryItemDraft) -> Unit,
    onSubmitAndAddAnother: (PantryItemDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val editing = state.editing
    val revision = state.editorRevision
    val separator = remember { decimalSeparatorFor(platformLanguageTags()) }
    val focus = remember { FocusRequester() }
    val picked = pickedOption(form.pickedId.value, state.ingredients, editing)

    // Back to the search after "save and add another", so the next item is typed at once.
    LaunchedEffect(revision) { if (revision > 0) focus.requestFocus() }

    EditorHeader(editedName = editing?.name, onDelete = onDelete)
    AddedConfirmation(name = state.lastAdded, revision = revision)
    IngredientPicker(state = state, form = form, picked = picked, focusRequester = focus)
    // Keyed: the field keeps its raw text locally and must start empty after an add.
    key(revision) {
        QuantityField(
            amountLabel = stringResource(Res.string.pantry_amount),
            units = state.units.map { (ref, label) -> ref.term.value to label },
            onChange = { typed, chosenUnit ->
                form.amount.value = typed
                form.unitId.value = chosenUnit
            },
            initialAmount = editing?.amount?.let { held -> formatAmount(held, separator) }.orEmpty(),
            initialUnitId = (editing?.unit ?: state.units.firstOrNull()?.first)?.term?.value,
            options =
                QuantityFieldOptions(
                    unitLabel = stringResource(Res.string.pantry_editor_unit),
                    amountPlaceholder = formatAmount(DEFAULT_AMOUNT, separator),
                    malformedMessage = stringResource(Res.string.pantry_editor_amount_malformed),
                    preferredUnitId = form.preferredUnitId.value,
                    onMalformedChange = { wrong -> form.malformed.value = wrong },
                ),
        )
    }
    LocationChips(
        options = state.locations,
        selected = form.location.value,
        onToggle = { ref -> form.location.value = if (form.location.value == ref) null else ref },
    )
    ExpiryField(value = form.expiresAt.value, onChange = { date -> form.expiresAt.value = date })
    EditorActions(
        draft = form.draft(state, picked),
        canAddAnother = editing == null,
        onSubmit = onSubmit,
        onSubmitAndAddAnother = onSubmitAndAddAnother,
    )
}

/**
 * The fields the sheet collects. Plain values are saveable so a rotation cannot turn a pick into
 * loose text; the chosen place and date are not, as before.
 */
private class EditorForm(
    val pickedId: MutableState<String?>,
    val query: MutableState<String>,
    val amount: MutableState<Double?>,
    val malformed: MutableState<Boolean>,
    val unitId: MutableState<String?>,
    val preferredUnitId: MutableState<String?>,
    val location: MutableState<TermRef?>,
    val expiresAt: MutableState<Instant?>,
) {
    fun draft(
        state: PantryUiState,
        picked: IngredientOption?,
    ): PantryItemDraft? =
        draftOf(
            picked = picked,
            text = query.value,
            amount = amount.value,
            malformed = malformed.value,
            unit = shownUnit(unitId.value, picked, state.units),
            location = location.value,
            expiresAt = expiresAt.value,
        )
}

/** Every field restarts when the row changes or a save keeps the sheet open: [revision] says which. */
@Composable
private fun rememberEditorForm(
    editing: PantryItemUi?,
    revision: Int,
    prefill: String?,
): EditorForm =
    EditorForm(
        pickedId = rememberSaveable(editing, revision) { mutableStateOf(editing?.ingredient?.value) },
        query = rememberSaveable(editing, revision, prefill) { mutableStateOf(editing?.name ?: prefill.orEmpty()) },
        amount = rememberSaveable(editing, revision) { mutableStateOf(editing?.amount) },
        malformed = rememberSaveable(editing, revision) { mutableStateOf(false) },
        unitId = rememberSaveable(editing, revision) { mutableStateOf(editing?.unit?.term?.value) },
        preferredUnitId = rememberSaveable(editing, revision) { mutableStateOf(editing?.unit?.term?.value) },
        location = remember(editing, revision) { mutableStateOf(editing?.location) },
        expiresAt = remember(editing, revision) { mutableStateOf(editing?.expiresAt) },
    )

/** A title so the sheet says what it is, and on an existing row a Delete beside it. */
@Composable
private fun EditorHeader(
    editedName: String?,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                if (editedName == null) {
                    stringResource(Res.string.pantry_editor_title_add)
                } else {
                    stringResource(Res.string.pantry_editor_title_edit, editedName)
                },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (editedName != null) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.pantry_delete)) }
        }
    }
}

/** Shown for a moment after "save and add another"; a screen reader hears it without moving focus. */
@Composable
private fun AddedConfirmation(
    name: String?,
    revision: Int,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(name, revision) {
        visible = name != null
        if (visible) {
            delay(CONFIRMATION_MILLIS)
            visible = false
        }
    }
    if (visible && name != null) {
        Text(
            text = stringResource(Res.string.pantry_editor_added, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private const val CONFIRMATION_MILLIS = 2_500L

/** Save, and for a new item the way to keep going: the sheet stays open on an empty form. */
@Composable
private fun EditorActions(
    draft: PantryItemDraft?,
    canAddAnother: Boolean,
    onSubmit: (PantryItemDraft) -> Unit,
    onSubmitAndAddAnother: (PantryItemDraft) -> Unit,
) {
    Button(
        onClick = { draft?.let(onSubmit) },
        enabled = draft != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(Res.string.pantry_save)) }
    if (canAddAnother) {
        OutlinedButton(
            onClick = { draft?.let(onSubmitAndAddAnother) },
            enabled = draft != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.pantry_editor_save_and_add)) }
    }
}

/**
 * Searchable rather than scrollable: a real catalogue is thousands of entries long, so nothing is
 * listed until something is typed. A pick fills the field, which is what will be saved.
 */
@Composable
private fun IngredientPicker(
    state: PantryUiState,
    form: EditorForm,
    picked: IngredientOption?,
    focusRequester: FocusRequester,
) {
    val query = form.query.value
    val matches = remember(state.ingredients, query) { state.ingredients.rankedMatches(query) }
    val searching = picked == null && query.isNotBlank()
    val clearLabel = stringResource(Res.string.pantry_editor_clear_search)
    val pick: (IngredientOption) -> Unit = { chosen ->
        form.query.value = chosen.label
        form.pickedId.value = chosen.id.value
        form.preferredUnitId.value = chosen.defaultUnit?.term?.value
    }

    OutlinedTextField(
        value = query,
        // Typing again after a pick is changing your mind, not refining it: the pick goes with
        // the text that no longer names it.
        onValueChange = { input ->
            form.query.value = input
            form.pickedId.value = null
        },
        label = { Text(stringResource(Res.string.pantry_search)) },
        // Said while it is true: what is typed and not picked is saved as the user wrote it.
        supportingText =
            if (searching) {
                { Text(stringResource(Res.string.pantry_editor_free_text_hint)) }
            } else {
                null
            },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(
                        onClick = {
                            form.query.value = ""
                            form.pickedId.value = null
                        },
                    ) { Icon(Icons.Filled.Close, contentDescription = clearLabel) }
                }
            } else {
                null
            },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
    if (searching) {
        // A plain column: the sheet already scrolls, and six rows need no lazy list inside it.
        Column {
            matches.forEach { option ->
                key(option.id.value) {
                    ListItem(
                        headlineContent = { Text(option.label) },
                        modifier = Modifier.clickable { pick(option) },
                    )
                }
            }
        }
    } else if (picked == null && state.recent.isNotEmpty()) {
        RecentChips(options = state.recent, onPick = pick)
    }
}

@Composable
private fun RecentChips(
    options: List<IngredientOption>,
    onPick: (IngredientOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.small)) {
        Text(
            text = stringResource(Res.string.pantry_editor_recent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.small),
            verticalArrangement = Arrangement.spacedBy(Dimens.small),
        ) {
            options.forEach { option ->
                key(option.id.value) {
                    TermChip(label = option.label, selected = false, onToggle = { onPick(option) })
                }
            }
        }
    }
}

/** Absent rather than empty: a pantry whose vocabulary has no storage places offers none. */
@Composable
private fun LocationChips(
    options: List<Pair<TermRef, String>>,
    selected: TermRef?,
    onToggle: (TermRef) -> Unit,
) {
    if (options.isEmpty()) return
    // Wraps instead of scrolling: a row inside a vertically scrolling sheet clipped its last chip.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.small),
        verticalArrangement = Arrangement.spacedBy(Dimens.small),
    ) {
        options.forEach { (ref, label) ->
            key(ref.term.value) {
                TermChip(label = label, selected = ref == selected, onToggle = { onToggle(ref) })
            }
        }
    }
}
