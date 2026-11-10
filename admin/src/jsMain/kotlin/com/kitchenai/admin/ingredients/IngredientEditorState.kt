package com.kitchenai.admin.ingredients

data class IngredientEditorState(
    val draft: IngredientDraft,
    val isBusy: Boolean = false,
    /** What the last save, upload or delete said when it failed. */
    val message: String? = null,
    /** Catalogue recipes that name this ingredient: null until checked, and always for a new one. */
    val references: List<String>? = null,
    /** The id typed again to confirm a hard delete. */
    val deleteConfirmation: String = "",
) {
    val canDelete: Boolean
        get() = !draft.isNew && references?.isEmpty() == true && deleteConfirmation == draft.id && !isBusy
}
