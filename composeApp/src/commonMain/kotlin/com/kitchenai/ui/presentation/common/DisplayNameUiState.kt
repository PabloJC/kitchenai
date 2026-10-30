package com.kitchenai.ui.presentation.common

/** The viewer's own name as the kitchen shows it, and the dialog that changes it. */
data class DisplayNameUiState(
    /** Null when none is set: a guest, or an Apple sign-in that kept its name hidden. */
    val name: String? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val error: UiText? = null,
)
