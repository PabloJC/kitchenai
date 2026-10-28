package com.kitchenai.ui.presentation.pantry

/** What the sheet can do to the row it was opened on, besides saving it. */
class EditingRowActions(
    val onDelete: () -> Unit,
    val onAddToList: () -> Unit,
)
