package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.model.resolve
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_own_data

internal fun uiState(
    draft: ProfileDraft?,
    catalogue: CatalogueState,
    save: SaveState,
    account: AccountState,
    failure: ProfileError?,
): ProfileUiState {
    val profile = draft?.profile
    return ProfileUiState(
        sections = sections(catalogue, profile),
        isCatalogueLoaded = catalogue.answered,
        hasCatalogueFailed = catalogue.failed,
        isLoading = draft == null,
        isSaving = save.saving,
        hasUnsavedChanges = draft?.edited == true,
        isSaved = save.saved && !save.saving && draft?.edited != true,
        hasAccount = account.session.hasAccount(),
        displayName = profile?.displayName,
        isAuthenticating = account.authenticating,
        isConfirmingAccountSwitch = account.pendingSwitch != null,
        switchNeedsAppleSheet = account.pendingSwitch?.let { switch -> switch.retry == null } == true,
        error = failure,
    )
}

/** Anonymous and signed-out both show the same "sign in" affordance, so only this case is true. */
private fun Session?.hasAccount(): Boolean = this is Session.SignedIn && !isAnonymous

/**
 * One section per taxonomy the catalogue published, in its order. A label that resolves to
 * nothing falls back to the identifier: ugly and honest beats inventing a word.
 */
internal fun sections(
    catalogue: CatalogueState,
    profile: UserProfile?,
): List<ConstraintSectionUi> {
    val tags = profile?.languageTags.orEmpty()
    val resolver =
        LabelResolver(
            terms = catalogue.terms.values.flatten(),
            taxonomies = catalogue.taxonomies,
            languageTags = tags,
        )
    val strengths = profile?.constraints.orEmpty().associate { it.term to it.strength }
    // A vocabulary the app reads structurally — units, storage places — is not a matter of taste,
    // and offering it here let a user avoid a freezer. Which ones those are is the catalogue's
    // to say, so nothing is named: they are the ones that declare a purpose.
    return catalogue.taxonomies.filter { taxonomy -> taxonomy.purpose == null }.map { taxonomy ->
        ConstraintSectionUi(
            taxonomy = taxonomy.id,
            title = taxonomy.labels.resolve(tags, taxonomy.defaultLanguageTag) ?: taxonomy.id.value,
            terms =
                catalogue.terms[taxonomy.id].orEmpty().map { term ->
                    TermChipUi(
                        term = term.ref,
                        label = resolver.label(term.ref) ?: term.ref.term.value,
                        strength = strengths[term.ref],
                    )
                },
            error = catalogue.errors[taxonomy.id],
        )
    }
}

/**
 * [AppError.Validation.field] anchors the message under the input it names; the wording is never the
 * domain's. [isRetryable] is for a listener that failed, which [ProfileViewModel.retry] can reopen.
 */
internal fun AppError.toProfileError(isRetryable: Boolean = false): ProfileError {
    val field = (this as? AppError.Validation)?.field
    return ProfileError(field, describe(Res.string.error_unauthorized_own_data), isRetryable)
}
