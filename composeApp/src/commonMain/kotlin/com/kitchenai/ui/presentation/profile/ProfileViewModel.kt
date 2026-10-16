package com.kitchenai.ui.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.ConstraintStrength
import com.kitchenai.shared.domain.model.GoogleAccessToken
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

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

    // A write landed this session, so the screen can say "Saved" rather than nothing.
    private val saved = MutableStateFlow(false)

    // What the server holds, which a refused write reverts the chips to; and that write, kept for Retry.
    private var persisted: UserProfile? = null
    private var failedEdit: UserProfile? = null
    private var autosave: Job? = null
    private val session = MutableStateFlow<Session?>(null)
    private val authenticating = MutableStateFlow(false)

    // Set only while the switch dialog is open: a refused link is answered by the user, not retried here.
    private val pendingSwitch = MutableStateFlow<PendingSwitch?>(null)
    private val account = combine(session, authenticating, pendingSwitch, ::AccountState)

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
    private var profileListener: Job? = null
    private var catalogueListener: Job? = null
    private var termListeners: Job? = null

    // Which uid the profile listener follows; start() is handed a new one after a switch to another account (#250).
    private val activeUserId = MutableStateFlow<UserId?>(null)

    val state: StateFlow<ProfileUiState> =
        combine(draft, catalogue, combine(saving, saved, ::SaveState), account, failure, ::uiState)
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

    /** Re-subscribes the profile and catalogue listeners after one failed; drafted edits survive it. */
    fun retry() {
        if (!started) return
        failedEdit?.let { edit ->
            // A refused write is answered by trying that same change again, not by reopening listeners.
            failedEdit = null
            writeFailure.value = null
            draft.value = ProfileDraft(edit, edited = true)
            scheduleSave()
            return
        }
        profileFailure.value = null
        catalogueFailure.value = null
        catalogue.update { state -> state.copy(answered = state.answered && !state.failed, failed = false) }
        termListeners?.cancel()
        profileListener?.cancel()
        catalogueListener?.cancel()
        watchProfile()
        watchCatalogue()
    }

    /** Prefer, Avoid, or off when [strength] is null; written on its own shortly after the last change. */
    fun setStrength(
        term: TermRef,
        strength: ConstraintStrength?,
    ) = edit { profile -> profile.withStrength(term, strength) }

    /**
     * [displayName] is Google's own and goes no further than the profile (#189): no email or photo URL.
     * A guest is linked and keeps their data; a Google account with data of its own asks before switching.
     */
    fun signInWithGoogle(
        token: GoogleIdToken,
        displayName: String?,
        accessToken: GoogleAccessToken? = null,
    ) = authenticate(displayName) {
        accountDelegate.signInWithGoogle(token, accessToken).askOnConflict {
            PendingSwitch(displayName) { accountDelegate.switchToGoogleAccount(token, accessToken) }
        }
    }

    /** [signInWithGoogle] for Apple. Apple sends the name only on an Apple ID's first authorisation. */
    fun signInWithApple(
        token: AppleIdToken,
        rawNonce: AppleRawNonce,
        displayName: String?,
    ) = authenticate(displayName) {
        // No retry kept: the refused link consumed the nonce, so a switch needs a fresh token from the sheet.
        accountDelegate.signInWithApple(token, rawNonce).askOnConflict { PendingSwitch(displayName, retry = null) }
    }

    /** The user agreed to leave the guest data behind; Google's tokens are reused to sign in to that account. */
    fun confirmAccountSwitch() {
        val switch = pendingSwitch.value ?: return
        val retry = switch.retry ?: return
        pendingSwitch.value = null
        authenticate(switch.displayName, retry)
    }

    /** The confirmed Apple switch, with the token the screen fetched again from Apple's sheet. */
    fun switchToAppleAccount(
        token: AppleIdToken,
        rawNonce: AppleRawNonce,
        displayName: String?,
    ) {
        pendingSwitch.value = null
        authenticate(displayName) { accountDelegate.switchToAppleAccount(token, rawNonce) }
    }

    /** Staying a guest changes nothing: the anonymous uid and everything under it are untouched. */
    fun cancelAccountSwitch() {
        pendingSwitch.value = null
    }

    /** A refused link opens the switch dialog instead of failing; a success with no session tells [authenticate]. */
    private fun AppResult<Session.SignedIn>.askOnConflict(switch: () -> PendingSwitch): AppResult<Session.SignedIn?> {
        if (this !is AppResult.Failure || error !is AppError.Conflict) return this
        accountDelegate.pendingDisplayName.dropUnbound()
        pendingSwitch.value = switch()
        return AppResult.Success(null)
    }

    /** A cancelled or failed Google or Apple sheet never reaches a use case but still owes the user a reason. */
    fun onSignInSheetFailed(error: AppError) {
        writeFailure.value = error.toProfileError()
    }

    /** A success with no session is the switch dialog taking over: no uid to bind, no failure to show. */
    private fun authenticate(
        displayName: String?,
        attempt: suspend () -> AppResult<Session.SignedIn?>,
    ) {
        if (authenticating.value) return
        authenticating.value = true
        writeFailure.value = null
        // Handed over before the sign-in: a failed switch tears this screen down before it answers.
        displayName?.takeUnless(String::isBlank)?.let(accountDelegate.pendingDisplayName::expect)
        viewModelScope.launch {
            when (val result = attempt()) {
                is AppResult.Failure -> {
                    accountDelegate.pendingDisplayName.dropUnbound()
                    writeFailure.value = result.error.toProfileError()
                }
                is AppResult.Success -> result.data?.userId?.let(accountDelegate.pendingDisplayName::bind)
            }
            authenticating.value = false
        }
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

    /** Deleted on the server, then signed out: the session starts again as a guest and rebuilds every screen. */
    fun deleteAccount() {
        if (authenticating.value) return
        authenticating.value = true
        writeFailure.value = null
        viewModelScope.launch {
            val result = accountDelegate.deleteAccount(NoParams)
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
        failedEdit = null
        draft.update { current -> current?.let { ProfileDraft(block(it.profile), edited = true) } }
        scheduleSave()
    }

    /** Every change restarts the wait, so a run of taps is one write; a write once begun is never cut off. */
    private fun scheduleSave() {
        autosave?.cancel()
        autosave =
            viewModelScope.launch {
                delay(AUTOSAVE_DELAY)
                withContext(NonCancellable) { persist() }
            }
    }

    private suspend fun persist() {
        val editing = draft.value?.takeIf { it.edited }?.profile ?: return
        saving.value = true
        when (val result = saveUserProfile(editing)) {
            is AppResult.Failure -> {
                // Back to what is stored, unless a newer change has already replaced this one.
                draft.update { current -> if (current?.profile == editing) reverted(current) else current }
                failedEdit = editing.takeUnless { result.error is AppError.Validation }
                writeFailure.value = result.error.toProfileError(isRetryable = failedEdit != null)
            }
            is AppResult.Success -> {
                persisted = editing
                saved.value = true
                draft.update { current -> if (current?.profile == editing) current.copy(edited = false) else current }
            }
        }
        saving.value = false
    }

    private fun reverted(current: ProfileDraft): ProfileDraft =
        persisted?.let(::ProfileDraft) ?: current.copy(edited = false)

    /** Off removes it; Prefer or Avoid replace whatever it was. The list edit stays in the use case that owns it. */
    private fun UserProfile.withStrength(
        term: TermRef,
        strength: ConstraintStrength?,
    ): UserProfile {
        val current = constraints.firstOrNull { it.term == term }?.strength
        if (current == strength) return this
        val cleared = current?.let { toggleDietaryConstraint(this, term, it) } ?: this
        return strength?.let { toggleDietaryConstraint(cleared, term, it) } ?: cleared
    }

    // Display only: the active uid comes from start(), as the raw session runs ahead of its setup.
    private fun watchSession() {
        viewModelScope.launch {
            accountDelegate.observeSession().collect { current -> session.value = current }
        }
    }

    /** [collectLatest] tears down the previous uid's listeners before opening the new one's. */
    private fun watchProfile() {
        profileListener =
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
        persisted = loaded
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
        profileFailure.value = error.toProfileError(isRetryable = true)
    }

    private fun watchCatalogue() {
        catalogueListener =
            viewModelScope.launch {
                launch { observeTaxonomies().collect { loaded -> onTaxonomies(loaded) } }
                launch {
                    observeTaxonomies.errors().collect { error ->
                        catalogue.update { state -> state.copy(answered = true, failed = true) }
                        catalogueFailure.value = error.toProfileError(isRetryable = true)
                    }
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

/** A write in flight, and whether one has landed since the screen opened. */
internal data class SaveState(
    val saving: Boolean,
    val saved: Boolean,
)

/** The profile being edited, and whether it holds changes that have not been written. */
internal data class ProfileDraft(
    val profile: UserProfile,
    val edited: Boolean = false,
)

/** What the account section needs: the session as Firebase reports it, and whether an action is in flight. */
internal data class AccountState(
    val session: Session?,
    val authenticating: Boolean,
    val pendingSwitch: PendingSwitch? = null,
)

/**
 * A refused link waiting for the user's answer. [retry] reuses Google's tokens so its sheet does not open
 * twice; null means Apple, whose sheet must be shown again for a fresh nonce.
 */
internal class PendingSwitch(
    val displayName: String?,
    val retry: (suspend () -> AppResult<Session.SignedIn>)?,
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

/** Long enough that a run of taps is one write, short enough that leaving the tab right after still saves. */
private val AUTOSAVE_DELAY = 600.milliseconds
