package com.kitchenai.admin.requests

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

private const val CONVERT_HINT = "Moves the pantry and shopping items still holding this text onto the ingredient"

private val HEADINGS = listOf("Text", "Typed", "Now in use", "First seen", "Last seen", "Status", "")

@Composable
fun RequestsTable(
    state: RequestsUiState,
    onPromote: (IngredientRequest) -> Unit,
    onAlias: (IngredientRequest) -> Unit,
    onDismiss: (IngredientRequest) -> Unit,
    onReopen: (IngredientRequest) -> Unit,
    onConvert: (IngredientRequest) -> Unit,
) {
    Table({ classes("catalogue-table") }) {
        Thead { Tr { HEADINGS.forEach { heading -> Th { Text(heading) } } } }
        Tbody {
            state.shown.forEach { request ->
                val busy = state.working == request.id
                Tr({ if (request.status == RequestStatus.DISMISSED) classes("archived") }) {
                    Td { Text(request.text) }
                    Td { Text(request.count.toString()) }
                    Td { Text(request.activeCount.toString()) }
                    Td { Text(shortDate(request.firstSeenMillis)) }
                    Td { Text(shortDate(request.lastSeenMillis)) }
                    Td {
                        Text(request.status.label)
                        request.ingredientId?.let { id ->
                            Text(" → ")
                            Code { Text(id) }
                        }
                        request.migratedAtMillis?.let { millis ->
                            Div({ classes("muted") }) { Text("Items converted ${shortDate(millis)}") }
                        }
                    }
                    Td({ classes("row-actions") }) {
                        if (request.status == RequestStatus.OPEN) {
                            Button({ onClick { onPromote(request) } }) { Text("Promote") }
                            Button({ onClick { onAlias(request) } }) { Text("Alias") }
                            Button({
                                if (busy) disabled()
                                onClick { onDismiss(request) }
                            }) { Text("Dismiss") }
                        } else {
                            Button({
                                if (busy) disabled()
                                onClick { onReopen(request) }
                            }) { Text("Reopen") }
                        }
                        if (request.status.needsIngredient) {
                            Button({
                                if (busy) disabled()
                                attr("title", CONVERT_HINT)
                                onClick { onConvert(request) }
                            }) { Text("Convert items") }
                        }
                    }
                }
            }
        }
    }
}
