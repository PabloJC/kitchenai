package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.UiText

/** The field name `SaveUserProfileUseCase` reports; the screen anchors its messages to it. */
const val CONSTRAINTS_FIELD = "constraints"

/**
 * Everything the profile screen draws. The shapes below belong to this one state and mean
 * nothing apart from it, which is why they share a file.
 */
data class ProfileUiState(
    val sections: List<ConstraintSectionUi> = emptyList(),
    // False while the catalogue is still on its way: an empty vocabulary and one that has not
    // arrived look identical on screen otherwise, and only one of them is worth a message.
    val isCatalogueLoaded: Boolean = false,
    // Only true when the listener actually failed, so an empty catalogue is not called broken.
    val hasCatalogueFailed: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: ProfileError? = null,
) {
    /** A message belongs to an input only when the use case named that input. */
    fun errorFor(field: String): UiText? = error?.takeIf { it.field == field }?.message

    /**
     * What is left once every field has taken its own: the screen shows it beside the save
     * button. A failure naming a field this screen does not bind still shows up here, because
     * the alternative is dropping it and telling the user nothing.
     */
    val generalError: UiText? get() = error?.takeIf { it.field !in BOUND_FIELDS }?.message
}

/** [field] is null when the failure belongs to the screen rather than to one of its inputs. */
data class ProfileError(
    val field: String?,
    val message: UiText,
)

/**
 * One taxonomy as a section. [title] is already resolved, and [error] is that taxonomy's own
 * listener failing — which costs this section its terms and every other section nothing.
 */
data class ConstraintSectionUi(
    val taxonomy: TaxonomyId,
    val title: String,
    val terms: List<TermChipUi>,
    val error: UiText? = null,
)

/** No [strength] is what "not selected" means, so the two can never disagree. */
data class TermChipUi(
    val term: TermRef,
    val label: String,
    val strength: ConstraintStrength?,
) {
    val selected: Boolean get() = strength != null
}

/** The inputs that render their own message; anything else falls through to the general one. */
private val BOUND_FIELDS = setOf(CONSTRAINTS_FIELD)
