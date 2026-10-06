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

/**
 * The profile screen's one state. How many sections there are, what they are called and what
 * is inside them comes from the catalogue alone; nothing here knows a single term.
 */
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

    // Which uid the profile listener follows. Google sign-in swaps the Firebase user outright
    // rather than linking (#186), so it changes mid-session, whenever start() is handed a new one.
    private val activeUserId = MutableStateFlow<UserId?>(null)

    // The name the platform launcher returned, waiting for the profile it belongs to: a brand
    // new Google account has no document yet, and an existing one only arrives once its own
    // listener answers. A plain field, not a flow — only ever touched from viewModelScope jobs,
    // all confined to Main, with no suspension between a read and the write that follows it.
    private var pendingDisplayName: String? = null

    val state: StateFlow<ProfileUiState> =
        combine(draft, catalogue, saving, account, failure, ::uiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    /**
     * The listeners start once however often the screen composes. A later call with another uid
     * is the session moving on: `SessionGate` only hands one over after that uid's setup
     * finished, so this screen never reads `users/{uid}` ahead of what [SessionViewModel] wrote.
     */
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

    /**
     * The platform sheet (#188), then [ProfileAccountDelegate.signInWithGoogle] with the token
     * it returned. [displayName] is Google's own, carried no further than
     * [UserProfile.displayName] once a profile exists to hold it (#189) — no email, no photo
     * URL enters domain state.
     */
    fun signInWithGoogle(
        token: GoogleIdToken,
        displayName: String?,
    ) {
        if (authenticating.value) return
        authenticating.value = true
        writeFailure.value = null
        pendingDisplayName = displayName?.takeUnless(String::isBlank)
        viewModelScope.launch {
            when (val result = accountDelegate.signInWithGoogle(token)) {
                is AppResult.Failure -> {
                    pendingDisplayName = null
                    writeFailure.value = result.error.toProfileError()
                }
                is AppResult.Success -> onSignedIn(result.data.userId)
            }
            authenticating.value = false
        }
    }

    /** A cancelled or failed platform sheet never reaches a use case, but still owes the user a reason. */
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

    /**
     * A different uid arrives through [start], once [SessionViewModel] has set it up, and its
     * profile applies the pending name then. The same uid signed in again (a token refresh) never
     * restarts the listener, so nothing else would apply it.
     */
    private fun onSignedIn(userId: UserId) {
        if (userId == activeUserId.value) draft.value?.profile?.let(::applyPendingDisplayName)
    }

    /** The account changed identity, not just its data: the previous uid's draft belongs to a different profile. */
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

    /**
     * A strength change is a removal followed by an insertion, so the list edit stays inside
     * the use case that owns it instead of being written again here.
     */
    private fun UserProfile.cycled(term: TermRef): UserProfile {
        val next = constraints.firstOrNull { it.term == term }?.strength?.next() ?: return this
        return toggleDietaryConstraint(toggleDietaryConstraint(this, term, next), term, next)
    }

    // Display only: the active uid comes from start(), never from the raw session stream, which
    // runs ahead of the setup SessionViewModel finishes for it.
    private fun watchSession() {
        viewModelScope.launch {
            accountDelegate.observeSession().collect { current -> session.value = current }
        }
    }

    /**
     * Reactive rather than a one-off pair of listeners from [start]: [activeUserId] changes
     * whenever [signInWithGoogle] swaps the account, and [collectLatest] tears down the
     * previous uid's listeners before opening the new one's.
     */
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

    /** A remote update never overwrites edits that have not been saved yet. */
    private fun onProfile(loaded: UserProfile) {
        profileFailure.value = null
        draft.update { current -> if (current?.edited == true) current else ProfileDraft(loaded) }
        applyPendingDisplayName(loaded)
    }

    /**
     * Skipped once the profile already carries the name, so a write's own echo does not save it
     * twice. The fake local-cache echo a Firestore listener gives for free never arrives for a
     * write this class itself made, so [draft] is updated here rather than waiting for one.
     */
    private fun applyPendingDisplayName(loaded: UserProfile) {
        val name = pendingDisplayName ?: return
        pendingDisplayName = null
        if (loaded.displayName == name) return
        val updated = loaded.copy(displayName = name)
        viewModelScope.launch {
            when (val result = saveUserProfile(updated)) {
                is AppResult.Failure -> writeFailure.value = result.error.toProfileError()
                is AppResult.Success ->
                    draft.update { current -> if (current?.edited == true) current else ProfileDraft(updated) }
            }
        }
    }

    // SessionViewModel is the only writer of a missing users/{uid}; a second one here would race
    // it and could drop the display name. A pending name is applied once the profile arrives.
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
