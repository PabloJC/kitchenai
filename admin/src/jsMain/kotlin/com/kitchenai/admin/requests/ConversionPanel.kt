package com.kitchenai.admin.requests

import androidx.compose.runtime.Composable
import com.kitchenai.admin.ingredients.IngredientsUiState
import com.kitchenai.admin.ingredients.displayLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun ConversionPanel(
    conversion: ConversionState,
    target: String,
    model: RequestsModel,
    scope: CoroutineScope,
) {
    val preview = conversion.preview
    Div({ classes("editor") }) {
        H2 { Text("Convert \"${conversion.request.text}\"") }
        P {
            Text(
                "${preview.pantryItems} pantry and ${preview.shoppingItems} shopping items will become $target. " +
                    "Their amounts, units, places and dates stay; the typed text goes.",
            )
        }
        P({ classes("muted") }) { Text("There is no undo. Kitchens are not shown: only how many items change.") }
        conversion.message?.let { message -> P({ classes("error") }) { Text(message) } }
        Div({ classes("editor-actions") }) {
            Button({
                classes("destructive")
                if (conversion.isBusy) disabled()
                onClick { scope.launch { model.convert() } }
            }) { Text(if (conversion.isBusy) "Working…" else "Convert ${preview.total} items") }
            Button({ onClick { model.cancelConversion() } }) { Text("Cancel") }
        }
    }
}

/** The target as the admin reads it: its name, or its id once the catalogue no longer lists it. */
fun catalogueName(
    conversion: ConversionState,
    ingredients: IngredientsUiState,
): String {
    val id = conversion.request.ingredientId.orEmpty()
    return ingredients.ingredients
        .firstOrNull { it.id.value == id }
        ?.labels
        ?.displayLabel(id)
        ?.let { name -> "$name ($id)" } ?: id
}
