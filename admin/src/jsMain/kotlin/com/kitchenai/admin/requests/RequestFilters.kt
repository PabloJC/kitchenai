package com.kitchenai.admin.requests

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

private const val ALL = ""

@Composable
fun RequestFilters(
    state: RequestsUiState,
    model: RequestsModel,
) {
    val query = state.query
    Div({ classes("filters") }) {
        TextInput(query.text) {
            attr("placeholder", "Search the text")
            onInput { event -> model.onQueryChange(query.copy(text = event.value)) }
        }
        Select({
            onChange { event -> model.onQueryChange(query.copy(status = event.value?.let(RequestStatus::fromWire))) }
        }) {
            RequestStatus.entries.forEach { status ->
                Option(status.wire, { if (query.status == status) selected() }) { Text(status.label) }
            }
            Option(ALL, { if (query.status == null) selected() }) { Text("All") }
        }
        Label(attrs = { classes("inline-field") }) {
            Text("Typed in")
            TextInput(state.typedLanguage) {
                classes("short-input")
                attr("title", "The language a promoted ingredient is named in, and an alias proposes")
                onInput { event -> model.onTypedLanguageChange(event.value) }
            }
        }
    }
}
