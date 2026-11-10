package com.kitchenai.admin.ingredients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Text

@Composable
fun IngredientsSection(model: IngredientsModel) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.start(this) }

    Section({ classes("ingredients") }) {
        Div({ classes("section-header") }) {
            H1 { Text("Ingredients") }
            Button({ onClick { model.create() } }) { Text("New ingredient") }
        }
        state.error?.let { message -> P({ classes("error") }) { Text(message) } }
        IngredientFilters(state, onQueryChange = model::onQueryChange)
        when {
            state.isLoading -> P { Text("Loading…") }
            state.shown.isEmpty() -> P({ classes("muted") }) { Text("No ingredient matches.") }
            else ->
                IngredientsTable(
                    state = state,
                    onEdit = model::edit,
                    onArchive = { ingredient, archived -> scope.launch { model.setArchived(ingredient, archived) } },
                )
        }
        state.editor?.let { editor -> IngredientEditor(editor, state, model, scope) }
    }
}
