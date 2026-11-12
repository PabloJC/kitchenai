package com.kitchenai.admin.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.kitchenai.admin.ingredients.IngredientEditor
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

@Composable
fun RequestsSection(model: RequestsModel) {
    val state by model.state.collectAsState()
    val ingredients by model.ingredients.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.start(this) }

    Section({ classes("requests") }) {
        Div({ classes("section-header") }) { H1 { Text("Free-text queue") } }
        P({ classes("muted") }) {
            Text("What people typed instead of picking an ingredient. No kitchen or person is stored with it.")
        }
        (state.error ?: ingredients.error)?.let { message -> P({ classes("error") }) { Text(message) } }
        RequestFilters(state, model)
        when {
            state.isLoading -> P { Text("Loading…") }
            state.shown.isEmpty() -> P({ classes("muted") }) { Text("Nothing in this view.") }
            else ->
                RequestsTable(
                    state = state,
                    onPromote = model::promote,
                    onAlias = model::startAlias,
                    onDismiss = { request -> scope.launch { model.dismiss(request) } },
                    onReopen = { request -> scope.launch { model.reopen(request) } },
                )
        }
        state.alias?.let { alias -> AliasEditor(alias, ingredients.ingredients, model, scope) }
        ingredients.editor?.let { editor -> IngredientEditor(editor, ingredients, model.ingredients, scope) }
    }
}
