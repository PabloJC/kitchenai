package com.kitchenai.ui.designsystem.component

/**
 * What a caller may add to a [QuantityField]. Every default leaves the field as it was, so a
 * screen that passes nothing is unchanged.
 *
 * [preferredUnitId] selects a unit from outside whenever it changes, e.g. an ingredient's own.
 * [unitLabel] names the selector for screen readers. [onMalformedChange] tells a blank field from
 * a wrong one, and [malformedMessage] is what is shown under the latter.
 */
data class QuantityFieldOptions(
    val unitLabel: String? = null,
    val amountPlaceholder: String? = null,
    val malformedMessage: String? = null,
    val preferredUnitId: String? = null,
    val onMalformedChange: (Boolean) -> Unit = {},
)
