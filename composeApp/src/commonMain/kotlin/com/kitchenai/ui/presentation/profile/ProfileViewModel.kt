package com.kitchenai.ui.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
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
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val draft = MutableStateFlow<ProfileDraft?>(null)
    private val catalogue = MutableStateFlow(CatalogueState())
    private val saving = MutableStateFlow(false)
    private val failure = MutableStateFlow<ProfileError?>(null)

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
        failure.value = null
        viewModelScope.launch(dispatchers.default) {
            when (val result = saveUserProfile(editing)) {
                is AppResult.Failure -> failure.value = result.error.toProfileError()
                is AppResult.Success -> draft.update { current -> current?.copy(edited = false) }
            }
            saving.value = false
        }
    }

    private fun edit(block: (UserProfile) -> UserProfile) {
        failure.value = null
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
        viewModelScope.launch(dispatchers.default) {
            observeUserProfile(userId).collect { loaded -> onProfile(loaded) }
        }
        viewModelScope.launch(dispatchers.default) {
            observeUserProfile.errors(userId).collect { error -> failure.value = error.toProfileError() }
        }
    }

    /** A remote update never overwrites edits that have not been saved yet. */
    private fun onProfile(loaded: UserProfile) {
        draft.update { current -> if (current?.edited == true) current else ProfileDraft(loaded) }
    }

    private fun watchCatalogue() {
        viewModelScope.launch(dispatchers.default) {
            observeTaxonomies().collect { loaded -> onTaxonomies(loaded) }
        }
        viewModelScope.launch(dispatchers.default) {
            observeTaxonomies.errors().collect { error -> failure.value = error.toProfileError() }
        }
    }

    private fun onTaxonomies(loaded: List<Taxonomy>) {
        catalogue.update { state -> state.copy(taxonomies = loaded) }
        // The term listeners belong to the catalogue that named them; a new catalogue replaces them.
        termListeners?.cancel()
        termListeners =
            viewModelScope.launch(dispatchers.default) {
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
    val taxonomies: List<Taxonomy> = emptyList(),
    val terms: Map<TaxonomyId, List<Term>> = emptyMap(),
    val errors: Map<TaxonomyId, String> = emptyMap(),
) {
    fun withTerms(
        id: TaxonomyId,
        loaded: List<Term>,
    ): CatalogueState = copy(terms = terms + (id to loaded), errors = errors - id)

    fun withError(
        id: TaxonomyId,
        message: String,
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
    return catalogue.taxonomies.map { taxonomy ->
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
        is AppError.Network -> ProfileError(null, "No connection")
        is AppError.Unauthorized -> ProfileError(null, "This account is not allowed to read its own data")
        is AppError.NotFound -> ProfileError(null, "Cannot find $resource")
        is AppError.Validation -> ProfileError(field, reason)
        is AppError.Unknown -> ProfileError(null, "Something went wrong")
    }
