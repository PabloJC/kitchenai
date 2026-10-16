package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.ui.presentation.common.UiText

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
    // A change waiting out the autosave delay or being written, so the screen can say "Saving".
    val hasUnsavedChanges: Boolean = false,
    // A write landed this session and nothing is pending: the quiet "Saved" that replaces a Save button.
    val isSaved: Boolean = false,
    // True only for a non-anonymous Session.SignedIn: an anonymous session is drawn the same
    // way as a signed-out one, since neither has an account to show.
    val hasAccount: Boolean = false,
    // The provider's own name, never an email or a photo: UserProfile carries nothing else from it.
    val displayName: String? = null,
    val isAuthenticating: Boolean = false,
    // The Google account already has a kitchen of its own: switching to it leaves the guest data behind (#250).
    val isConfirmingAccountSwitch: Boolean = false,
    // Apple's refused link consumed its nonce: confirming has to show Apple's sheet again before switching.
    val switchNeedsAppleSheet: Boolean = false,
    val error: ProfileError? = null,
) {
    /** Every failure, whichever field it names: with no inputs to anchor to, the banner is the one place to say it. */
    val generalError: UiText? get() = error?.message

    /** A listener that failed, or a write that may succeed if sent again; a refused (invalid) write would not. */
    val canRetry: Boolean get() = error?.isRetryable == true
}

/**
 * [field] is null when the failure belongs to the screen rather than to one of its inputs.
 * [isRetryable] marks a listener failure, which the screen answers with a Retry.
 */
data class ProfileError(
    val field: String?,
    val message: UiText,
    val isRetryable: Boolean = false,
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
