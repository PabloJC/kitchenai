package com.kitchenai.admin.ingredients

import androidx.compose.runtime.Composable
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermId
import com.kitchenai.shared.domain.model.TermRef
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

private const val SEPARATOR = "/"

@Composable
fun IngredientFilters(
    state: IngredientsUiState,
    onQueryChange: (IngredientQuery) -> Unit,
) {
    val query = state.query
    Div({ classes("filters") }) {
        TextInput(query.text) {
            attr("placeholder", "Search by id or name")
            onInput { event -> onQueryChange(query.copy(text = event.value)) }
        }
        Select({ onChange { event -> onQueryChange(query.copy(tag = event.value?.toTermRef())) } }) {
            Option("", { if (query.tag == null) selected() }) { Text("Any tag") }
            state.tagTaxonomies.forEach { taxonomy ->
                state.terms[taxonomy.id].orEmpty().forEach { term ->
                    Option(term.ref.encoded(), { if (query.tag == term.ref) selected() }) {
                        Text(
                            "${taxonomy.labels.displayLabel(
                                taxonomy.id.value,
                            )}: ${term.labels.displayLabel(term.ref.term.value)}",
                        )
                    }
                }
            }
        }
        Select({ onChange { event -> onQueryChange(query.copy(archived = ArchivedFilter.valueOf(event.value!!))) } }) {
            ArchivedFilter.entries.forEach { filter ->
                Option(filter.name, { if (query.archived == filter) selected() }) { Text(filter.label) }
            }
        }
    }
}

/** A select only carries strings: a term travels as `taxonomy/term`, neither of which may hold a slash. */
fun TermRef.encoded(): String = "${taxonomy.value}$SEPARATOR${term.value}"

fun String.toTermRef(): TermRef? {
    val parts = split(SEPARATOR)
    if (parts.size != 2) return null
    val taxonomy = (TaxonomyId.of(parts[0]) as? AppResult.Success)?.data ?: return null
    val term = (TermId.of(parts[1]) as? AppResult.Success)?.data ?: return null
    return TermRef(taxonomy, term)
}
