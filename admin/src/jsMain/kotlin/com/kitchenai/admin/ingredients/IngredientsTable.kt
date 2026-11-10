package com.kitchenai.admin.ingredients

import androidx.compose.runtime.Composable
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.TermRef
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

private val HEADINGS = listOf("", "Id", "Names", "Default unit", "Tags", "")

@Composable
fun IngredientsTable(
    state: IngredientsUiState,
    onEdit: (Ingredient) -> Unit,
    onArchive: (Ingredient, Boolean) -> Unit,
) {
    Table({ classes("catalogue-table") }) {
        Thead { Tr { HEADINGS.forEach { heading -> Th { Text(heading) } } } }
        Tbody {
            state.shown.forEach { ingredient ->
                Tr({ if (ingredient.archived) classes("archived") }) {
                    Td {
                        ingredient.imageUrl?.let { url -> Img(url, ingredient.id.value) { classes("thumbnail") } }
                    }
                    Td { Code { Text(ingredient.id.value) } }
                    Td {
                        Text(
                            ingredient.labels.entries.joinToString(" · ") { (language, text) -> "$language: $text" },
                        )
                    }
                    Td { Text(ingredient.defaultUnit?.let { ref -> state.termLabel(ref) }.orEmpty()) }
                    Td { Text(ingredient.tags.joinToString(", ") { ref -> state.termLabel(ref) }) }
                    Td({ classes("row-actions") }) {
                        Button({ onClick { onEdit(ingredient) } }) { Text("Edit") }
                        Button({ onClick { onArchive(ingredient, !ingredient.archived) } }) {
                            Text(if (ingredient.archived) "Restore" else "Archive")
                        }
                    }
                }
            }
        }
    }
}

/** A term the vocabulary no longer lists still shows, as its id. */
fun IngredientsUiState.termLabel(ref: TermRef): String =
    terms[ref.taxonomy]
        ?.firstOrNull { term -> term.ref == ref }
        ?.labels
        ?.displayLabel(ref.term.value)
        ?: ref.term.value
