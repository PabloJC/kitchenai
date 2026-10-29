package com.kitchenai.ui.designsystem.component

import androidx.compose.ui.text.input.ImeAction

/**
 * What a caller may add to a [QuantityField]. Every default leaves the field as it was, so a
 * screen that passes nothing is unchanged.
 *
 * [preferredUnitId] selects a unit from outside whenever it changes, e.g. an ingredient's own.
 * [unitLabel] names the selector for screen readers. [onMalformedChange] tells a blank field from
 * a wrong one, and [malformedMessage] is what is shown under the latter. [imeAction] is the
 * keyboard's action key and [onImeAction] what it does; [doneLabel] names the Done button a
 * number pad with no Return key (iOS) gets instead, which only closes the keyboard.
 */
data class QuantityFieldOptions(
    val unitLabel: String? = null,
    val amountPlaceholder: String? = null,
    val malformedMessage: String? = null,
    val preferredUnitId: String? = null,
    val onMalformedChange: (Boolean) -> Unit = {},
    val imeAction: ImeAction = ImeAction.Default,
    val onImeAction: () -> Unit = {},
    val doneLabel: String? = null,
)
