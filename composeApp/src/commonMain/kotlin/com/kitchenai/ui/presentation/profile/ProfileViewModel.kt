package com.kitchenai.ui.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.model.resolve
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomies
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomy
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfile
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfile
import com.kitchenai.shared.domain.usecase.profile.ToggleDietaryConstraint
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_not_found
import com.kitchenai.ui.resources.error_timeout
import com.kitchenai.ui.resources.error_unauthorized_own_data
import com.kitchenai.ui.resources.error_unknown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The profile screen's one state. How many sections there are, what they are called and what
 * is inside them comes from the catalogue alone; nothing here knows a single term.
 */
class ProfileViewModel(
    private val observeUserProfile: ObserveUserProfile,
    private val observeTaxonomies: ObserveTaxonomies,
    private val observeTaxonomy: ObserveTaxonomy,
    private val saveUserProfile: SaveUserProfile,
    private val toggleDietaryConstraint: ToggleDietaryConstraint,
) : ViewModel() {
    private val draft = MutableStateFlow<ProfileDraft?>(null)
    private val catalogue = MutableStateFlow(CatalogueState())
    private val saving = MutableStateFlow(false)

    // One per source, each cleared by its own stream recovering: a profile that loads again must
    // not silence a catalogue that is still broken, and neither may outlive its own failure.
    private val profileFailure = MutableStateFlow<ProfileError?>(null)
    private val catalogueFailure = MutableStateFlow<ProfileError?>(null)
    private val writeFailure = MutableStateFlow<ProfileError?>(null)
    private val failure =
        combine(writeFailure, profileFailure, catalogueFailure) { streams ->
            // The write speaks first: it is the thing the user just did, and a stale banner from
            // a listener must not hide the reason their save was refused.
            streams.firstOrNull { it != null }
        }

    private var started = false
    private var termListeners: Job? = null

    val state: StateFlow<ProfileUiState> =
        combine(draft, catalogue, saving, failure, ::uiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    /** Idempotent: a configuration change composes the screen again and must not double the listeners. */
    fun start(userId: UserId) {
        if (started) return
        started = true
        watchProfile(userId)
        watchCatalogue()
    }

    fun setDisplayName(name: String) = edit { profile -> profile.copy(displayName = name.ifBlank { null }) }

    fun setServings(servings: Int) =
        edit { profile -> profile.copy(household = profile.household.copy(servings = servings)) }

    /** Which strength a first tap binds at is domain policy; this only asks for it. */
    fun toggleConstraint(term: TermRef) =
        edit { profile -> toggleDietaryConstraint(profile, term, ConstraintStrength.SOFTEST) }

    fun cycleStrength(term: TermRef) = edit { profile -> profile.cycled(term) }

    /**
     * The only write. Nothing above this saves, because a write per keystroke is a bill and a
     * sync storm; the flag also makes a double tap a single write.
     */
    fun save() {
        val editing = draft.value?.profile ?: return
        if (saving.value) return
        saving.value = true
        writeFailure.value = null
        viewModelScope.launch {
            when (val result = saveUserProfile(editing)) {
                is AppResult.Failure -> writeFailure.value = result.error.toProfileError()
                is AppResult.Success -> draft.update { current -> current?.copy(edited = false) }
            }
            saving.value = false
        }
    }

    private fun edit(block: (UserProfile) -> UserProfile) {
        writeFailure.value = null
        draft.update { current -> current?.let { ProfileDraft(block(it.profile), edited = true) } }
    }

    /**
     * A strength change is a removal followed by an insertion, so the list edit stays inside
     * the use case that owns it instead of being written again here.
     */
    private fun UserProfile.cycled(term: TermRef): UserProfile {
        val next = constraints.firstOrNull { it.term == term }?.strength?.next() ?: return this
        return toggleDietaryConstraint(toggleDietaryConstraint(this, term, next), term, next)
    }

    private fun watchProfile(userId: UserId) {
        viewModelScope.launch {
            observeUserProfile(userId).collect { loaded -> onProfile(loaded) }
        }
        viewModelScope.launch {
            observeUserProfile.errors(userId).collect { error ->
                profileFailure.value = error.toProfileError()
            }
        }
    }

    /** A remote update never overwrites edits that have not been saved yet. */
    private fun onProfile(loaded: UserProfile) {
        profileFailure.value = null
        draft.update { current -> if (current?.edited == true) current else ProfileDraft(loaded) }
    }

    private fun watchCatalogue() {
        viewModelScope.launch {
            observeTaxonomies().collect { loaded -> onTaxonomies(loaded) }
        }
        viewModelScope.launch {
            observeTaxonomies.errors().collect { error ->
                catalogue.update { state -> state.copy(answered = true, failed = true) }
                catalogueFailure.value = error.toProfileError()
            }
        }
    }

    private fun onTaxonomies(loaded: List<Taxonomy>) {
        catalogueFailure.value = null
        catalogue.update { state -> state.copy(answered = true, failed = false, taxonomies = loaded) }
        // The term listeners belong to the catalogue that named them; a new catalogue replaces them.
        termListeners?.cancel()
        termListeners =
            viewModelScope.launch {
                loaded.forEach { taxonomy -> watchTerms(taxonomy.id) }
            }
    }

    /** Keyed like the port: one broken taxonomy costs its own section and leaves the rest standing. */
    private fun CoroutineScope.watchTerms(id: TaxonomyId) {
        launch {
            observeTaxonomy(id).collect { terms -> catalogue.update { state -> state.withTerms(id, terms) } }
        }
        launch {
            observeTaxonomy.errors(id).collect { error ->
                catalogue.update { state -> state.withError(id, error.toProfileError().message) }
            }
        }
    }
}

/** The profile being edited, and whether it holds changes that have not been written. */
private data class ProfileDraft(
    val profile: UserProfile,
    val edited: Boolean = false,
)

/** The catalogue as the screen needs it: what exists, what is in it, and what failed to load. */
private data class CatalogueState(
    // Distinguishes a catalogue with nothing in it from one that has not answered yet: the
    // screen must not say the vocabulary failed to load while it is still on its way.
    val answered: Boolean = false,
    // Answered and failed are different answers: a catalogue with nothing in it is valid, and
    // telling that user the vocabulary could not be loaded would be a lie.
    val failed: Boolean = false,
    val taxonomies: List<Taxonomy> = emptyList(),
    val terms: Map<TaxonomyId, List<Term>> = emptyMap(),
    val errors: Map<TaxonomyId, UiText> = emptyMap(),
) {
    fun withTerms(
        id: TaxonomyId,
        loaded: List<Term>,
    ): CatalogueState = copy(terms = terms + (id to loaded), errors = errors - id)

    fun withError(
        id: TaxonomyId,
        message: UiText,
    ): CatalogueState = copy(errors = errors + (id to message))
}

private fun uiState(
    draft: ProfileDraft?,
    catalogue: CatalogueState,
    saving: Boolean,
    failure: ProfileError?,
): ProfileUiState {
    val profile = draft?.profile
    return ProfileUiState(
        displayName = profile?.displayName.orEmpty(),
        servings = profile?.household?.servings ?: 1,
        constraintCount = profile?.constraints?.size ?: 0,
        languageTags = profile?.languageTags.orEmpty(),
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
private fun sections(
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

/** The cause is dropped on purpose: it can carry paths and identifiers, and this ends up on screen. */
private fun AppError.toProfileError(): ProfileError =
    when (this) {
        is AppError.Network -> ProfileError(null, UiText.of(Res.string.error_no_connection))
        is AppError.Timeout -> ProfileError(null, UiText.of(Res.string.error_timeout))
        is AppError.Unauthorized -> ProfileError(null, UiText.of(Res.string.error_unauthorized_own_data))
        is AppError.NotFound -> ProfileError(null, UiText.of(Res.string.error_not_found, resource))
        // The reason alone, not "Invalid <field>: <reason>": this one renders under the input it
        // names, so repeating the field there would say the same thing twice. It is Raw because
        // the domain wrote it — there is no key for a sentence this module did not author.
        is AppError.Validation -> ProfileError(field, UiText.Raw(reason))
        is AppError.Unknown -> ProfileError(null, UiText.of(Res.string.error_unknown))
    }
