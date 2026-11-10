package com.kitchenai.admin.ingredients

import androidx.compose.runtime.Composable
import com.kitchenai.shared.domain.model.Taxonomy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Fieldset
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Legend
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

@Composable
fun IngredientEditor(
    editor: IngredientEditorState,
    state: IngredientsUiState,
    model: IngredientsModel,
    scope: CoroutineScope,
) {
    val draft = editor.draft
    val change = model::updateDraft
    val problem = draft.problem()
    Div({ classes("editor") }) {
        H2 { Text(if (draft.isNew) "New ingredient" else "Edit ${draft.id}") }
        Label {
            Text("Id")
            TextInput(draft.id) {
                if (!draft.isNew) disabled()
                attr("placeholder", "olive-oil")
                onInput { event -> change { it.copy(id = event.value.trim()) } }
            }
        }
        LabelRows(draft, change)
        UnitField(draft, state, change)
        state.tagTaxonomies.forEach { taxonomy -> TagField(taxonomy, draft, state, change) }
        Label {
            CheckboxInput(draft.purchasedWhole) {
                onChange { event -> change { it.copy(purchasedWhole = event.value) } }
            }
            Text("Sold as whole items (never a fraction)")
        }
        PictureField(editor, onPick = {
                file ->
            scope.launch { model.attachPicture(file) }
        }, onRemove = model::removePicture)
        (editor.message ?: problem)?.let { message -> P({ classes("error") }) { Text(message) } }
        Div({ classes("editor-actions") }) {
            Button({
                if (problem != null || editor.isBusy) disabled()
                onClick { scope.launch { model.save() } }
            }) { Text(if (editor.isBusy) "Working…" else "Save") }
            Button({ onClick { scope.launch { model.close() } } }) { Text("Cancel") }
        }
        if (!draft.isNew) DeleteField(editor, model, scope)
    }
}

@Composable
private fun LabelRows(
    draft: IngredientDraft,
    change: ((IngredientDraft) -> IngredientDraft) -> Unit,
) {
    Fieldset {
        Legend { Text("Names") }
        draft.labels.forEachIndexed { index, row ->
            Div({ classes("label-row") }) {
                TextInput(row.language) {
                    classes("language")
                    onInput { event -> change { it.withLabel(index) { label -> label.copy(language = event.value) } } }
                }
                TextInput(row.text) {
                    onInput { event -> change { it.withLabel(index) { label -> label.copy(text = event.value) } } }
                }
            }
        }
        Button({ onClick { change { it.copy(labels = it.labels + LabelRow("", "")) } } }) { Text("Add a language") }
    }
}

@Composable
private fun UnitField(
    draft: IngredientDraft,
    state: IngredientsUiState,
    change: ((IngredientDraft) -> IngredientDraft) -> Unit,
) {
    Label {
        Text("Default unit")
        Select({ onChange { event -> change { it.copy(defaultUnit = event.value?.toTermRef()) } } }) {
            Option("", { if (draft.defaultUnit == null) selected() }) { Text("None") }
            state.unitTaxonomies.forEach { taxonomy ->
                state.terms[taxonomy.id].orEmpty().forEach { term ->
                    Option(term.ref.encoded(), { if (draft.defaultUnit == term.ref) selected() }) {
                        Text(term.labels.displayLabel(term.ref.term.value))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagField(
    taxonomy: Taxonomy,
    draft: IngredientDraft,
    state: IngredientsUiState,
    change: ((IngredientDraft) -> IngredientDraft) -> Unit,
) {
    Fieldset({ classes("tags") }) {
        Legend { Text(taxonomy.labels.displayLabel(taxonomy.id.value)) }
        state.terms[taxonomy.id].orEmpty().forEach { term ->
            Label {
                CheckboxInput(term.ref in draft.tags) {
                    onChange { event ->
                        change { it.copy(tags = if (event.value) it.tags + term.ref else it.tags - term.ref) }
                    }
                }
                Text(term.labels.displayLabel(term.ref.term.value))
            }
        }
    }
}

@Composable
private fun PictureField(
    editor: IngredientEditorState,
    onPick: (org.w3c.files.File) -> Unit,
    onRemove: () -> Unit,
) {
    Fieldset {
        Legend { Text("Picture") }
        editor.draft.imageUrl?.let { url ->
            Img(url, editor.draft.id) { classes("preview") }
            Button({ onClick { onRemove() } }) { Text("Remove picture") }
        }
        Input(InputType.File) {
            attr("accept", "image/*")
            if (editor.isBusy) disabled()
            onChange { event -> event.target.files?.item(0)?.let(onPick) }
        }
        P({ classes("muted") }) { Text("Resized to 1024 px and saved as WebP. Replaced files are removed on save.") }
    }
}

@Composable
private fun DeleteField(
    editor: IngredientEditorState,
    model: IngredientsModel,
    scope: CoroutineScope,
) {
    Fieldset({ classes("danger") }) {
        Legend { Text("Delete") }
        P { Text("Archive instead whenever pantries may still hold it: a deleted id leaves their items nameless.") }
        val references = editor.references
        when {
            references == null ->
                Button({ onClick { scope.launch { model.checkReferences() } } }) { Text("Check catalogue recipes") }
            references.isNotEmpty() -> P { Text("Used by ${references.joinToString(", ")}: it cannot be deleted.") }
            else -> {
                Label {
                    Text("Type ${editor.draft.id} to delete it")
                    TextInput(editor.deleteConfirmation) {
                        onInput {
                                event ->
                            model.onDeleteConfirmationChange(event.value)
                        }
                    }
                }
                Button({
                    classes("destructive")
                    if (!editor.canDelete) disabled()
                    onClick { scope.launch { model.delete() } }
                }) { Text("Delete for good") }
            }
        }
    }
}

private fun IngredientDraft.withLabel(
    index: Int,
    change: (LabelRow) -> LabelRow,
): IngredientDraft = copy(labels = labels.mapIndexed { i, row -> if (i == index) change(row) else row })
