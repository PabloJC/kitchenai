package com.kitchenai.admin.requests

import androidx.compose.runtime.Composable
import com.kitchenai.admin.ingredients.displayLabel
import com.kitchenai.shared.domain.model.Ingredient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

@Composable
fun AliasEditor(
    alias: AliasState,
    catalogue: List<Ingredient>,
    model: RequestsModel,
    scope: CoroutineScope,
) {
    val target = alias.target(catalogue)
    val problem = alias.problem(catalogue)
    val choices = catalogue.filterNot { it.archived }.sortedBy { it.labels.displayLabel(it.id.value).lowercase() }
    Div({ classes("editor") }) {
        H2 { Text("\"${alias.request.text}\" is…") }
        Label {
            Text("An existing ingredient")
            Select({
                onChange {
                        event ->
                    model.updateAlias { it.copy(ingredientId = event.value?.ifEmpty { null }) }
                }
            }) {
                Option("", { if (target == null) selected() }) { Text("Pick one") }
                choices.forEach { ingredient ->
                    Option(ingredient.id.value, { if (ingredient == target) selected() }) {
                        Text("${ingredient.labels.displayLabel(ingredient.id.value)} (${ingredient.id.value})")
                    }
                }
            }
        }
        if (target != null && alias.isNewSpelling(target)) {
            Label {
                CheckboxInput(alias.addLabel) {
                    onChange {
                            event ->
                        model.updateAlias { it.copy(addLabel = event.value) }
                    }
                }
                Text("Also name it \"${alias.request.text}\" in")
                TextInput(alias.language) {
                    classes("short-input")
                    onInput { event -> model.updateAlias { it.copy(language = event.value.trim()) } }
                }
            }
            P({ classes("muted") }) { Text("Only a language it has no name in yet: a name is one per language.") }
        }
        (alias.message ?: problem)?.let { message -> P({ classes("error") }) { Text(message) } }
        Div({ classes("editor-actions") }) {
            Button({
                if (problem != null || alias.isBusy) disabled()
                onClick { scope.launch { model.alias() } }
            }) { Text(if (alias.isBusy) "Working…" else "Alias") }
            Button({ onClick { model.cancelAlias() } }) { Text("Cancel") }
        }
    }
}
