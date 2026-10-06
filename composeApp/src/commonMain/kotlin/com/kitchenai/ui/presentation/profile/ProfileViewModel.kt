package com.kitchenai.ui.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.Taxonomy
import com.kitchenai.shared.domain.model.TaxonomyId
import com.kitchenai.shared.domain.model.Term
import com.kitchenai.shared.domain.model.TermRef
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.usecase.NoParams
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomiesUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveTaxonomyUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.ToggleDietaryConstraintUseCase
import com.kitchenai.ui.presentation.common.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The profile screen's one state; sections and terms come from the catalogue alone, none are known here. */
class ProfileViewModel(
    private val observeUserProfile: ObserveUserProfileUseCase,
    private val observeTaxonomies: ObserveTaxonomiesUseCase,
    private val observeTaxonomy: ObserveTaxonomyUseCase,
    private val saveUserProfile: SaveUserProfileUseCase,
    private val toggleDietaryConstraint: ToggleDietaryConstraintUseCase,
    private val accountDelegate: ProfileAccountDelegate,
) : ViewModel() {
    private val draft = MutableStateFlow<ProfileDraft?>(null)
    private val catalogue = MutableStateFlow(CatalogueState())
    private val saving = MutableStateFlow(false)
    private val session = MutableStateFlow<Session?>(null)
    private val authenticating = MutableStateFlow(false)
    private val account = combine(session, authenticating, ::AccountState)

    // One per source, each cleared by its own stream recovering, so one source never silences another.
    private val profileFailure = MutableStateFlow<ProfileError?>(null)
    private val catalogueFailure = MutableStateFlow<ProfileError?>(null)
    private val writeFailure = MutableStateFlow<ProfileError?>(null)
    private val failure =
        combine(writeFailure, profileFailure, catalogueFailure) { streams ->
            // The write speaks first: a stale listener banner must not hide why the save was refused.
            streams.firstOrNull { it != null }
        }

    private var started = false
    private var termListeners: Job? = null

    // Which uid the profile listener follows; start() is handed a new one after a Google sign-in (#186).
    private val activeUserId = MutableStateFlow<UserId?>(null)

    val state: StateFlow<ProfileUiState> =
        combine(draft, catalogue, saving, account, failure, ::uiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    /** Listeners start once; another uid is the session moving on, handed over after its setup finished. */
    fun start(userId: UserId) {
        if (started) return switchActiveUser(userId)
        started = true
        activeUserId.value = userId
        watchSession()
        watchProfile()
        watchCatalogue()
    }

    /** Which strength a first tap binds at is domain policy; this only asks for it. */
    fun toggleConstraint(term: TermRef) =
        edit { profile -> toggleDietaryConstraint(profile, term, ConstraintStrength.SOFTEST) }

    fun cycleStrength(term: TermRef) = edit { profile -> profile.cycled(term) }

    /** The only write from the screen: one per tap, never per keystroke. */
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

    /** [displayName] is Google's own and goes no further than the profile (#189): no email or photo URL. */
    fun signInWithGoogle(
        token: GoogleIdToken,
        displayName: String?,
    ) {
        if (authenticating.value) return
        authenticating.value = true
        writeFailure.value = null
        // Handed over before the sign-in: a failed switch tears this screen down before it answers.
        displayName?.takeUnless(String::isBlank)?.let(accountDelegate.pendingDisplayName::expect)
        viewModelScope.launch {
            when (val result = accountDelegate.signInWithGoogle(token)) {
                is AppResult.Failure -> {
                    accountDelegate.pendingDisplayName.dropUnbound()
                    writeFailure.value = result.error.toProfileError()
                }
                is AppResult.Success -> accountDelegate.pendingDisplayName.bind(result.data.userId)
            }
            authenticating.value = false
        }
    }

    /** A cancelled or failed platform sheet never reaches a use case but still owes the user a reason. */
    fun onGoogleSignInFailed(error: AppError) {
        writeFailure.value = error.toProfileError()
    }

    fun signOut() {
        if (authenticating.value) return
        authenticating.value = true
        writeFailure.value = null
        viewModelScope.launch {
            val result = accountDelegate.signOut(NoParams)
            if (result is AppResult.Failure) writeFailure.value = result.error.toProfileError()
            authenticating.value = false
        }
    }

    /** The account changed identity, not just its data: the previous uid's draft is another profile's. */
    private fun switchActiveUser(userId: UserId) {
        if (activeUserId.value == userId) return
        profileFailure.value = null
        draft.value = null
        activeUserId.value = userId
    }

    private fun edit(block: (UserProfile) -> UserProfile) {
        writeFailure.value = null
        draft.update { current -> current?.let { ProfileDraft(block(it.profile), edited = true) } }
    }

    /** A strength change is a removal then an insertion, so the list edit stays in the use case that owns it. */
    private fun UserProfile.cycled(term: TermRef): UserProfile {
        val next = constraints.firstOrNull { it.term == term }?.strength?.next() ?: return this
        return toggleDietaryConstraint(toggleDietaryConstraint(this, term, next), term, next)
    }

    // Display only: the active uid comes from start(), as the raw session runs ahead of its setup.
    private fun watchSession() {
        viewModelScope.launch {
            accountDelegate.observeSession().collect { current -> session.value = current }
        }
    }

    /** [collectLatest] tears down the previous uid's listeners before opening the new one's. */
    private fun watchProfile() {
        viewModelScope.launch {
            activeUserId.filterNotNull().collectLatest { userId ->
                coroutineScope {
                    launch { observeUserProfile(userId).collect { loaded -> onProfile(loaded) } }
                    launch { observeUserProfile.errors(userId).collect { error -> onProfileError(error) } }
                }
            }
        }
    }

    /** Unsaved edits survive a remote update, but the name is not editable here, so the remote one wins. */
    private fun onProfile(loaded: UserProfile) {
        profileFailure.value = null
        draft.update { current ->
            if (current?.edited == true) {
                current.copy(profile = current.profile.copy(displayName = loaded.displayName))
            } else {
                ProfileDraft(loaded)
            }
        }
    }

    // SessionViewModel is the only writer of a missing users/{uid} and applies any pending display name.
    private fun onProfileError(error: AppError) {
        profileFailure.value = error.toProfileError()
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
internal data class ProfileDraft(
    val profile: UserProfile,
    val edited: Boolean = false,
)

/** What the account section needs: the session as Firebase reports it, and whether an action is in flight. */
internal data class AccountState(
    val session: Session?,
    val authenticating: Boolean,
)

/** The catalogue as the screen needs it: what exists, what is in it, and what failed to load. */
internal data class CatalogueState(
    // Tells a catalogue with nothing in it from one that has not answered yet.
    val answered: Boolean = false,
    // Answered and failed differ: an empty catalogue is valid and must not read as a load failure.
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
