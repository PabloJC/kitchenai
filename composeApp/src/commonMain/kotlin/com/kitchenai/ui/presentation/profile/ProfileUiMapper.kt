package com.kitchenai.ui.presentation.profile

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.model.resolve
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_own_data

internal fun uiState(
    draft: ProfileDraft?,
    catalogue: CatalogueState,
    saving: Boolean,
    failure: ProfileError?,
): ProfileUiState {
    val profile = draft?.profile
    return ProfileUiState(
        sections = sections(catalogue, profile),
        isCatalogueLoaded = catalogue.answered,
        hasCatalogueFailed = catalogue.failed,
        isLoading = draft == null,
        isSaving = saving,
        error = failure,
    )
}

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
 * [AppError.Validation.reason] alone, not "Invalid `<field>`: `<reason>`": this one renders under
 * the input it names, so repeating the field there would say the same thing twice. It is Raw
 * because the domain wrote it — there is no key for a sentence this module did not author.
 */
internal fun AppError.toProfileError(): ProfileError {
    val field = (this as? AppError.Validation)?.field
    val message = describe(Res.string.error_unauthorized_own_data, validation = { error -> UiText.Raw(error.reason) })
    return ProfileError(field, message)
}
