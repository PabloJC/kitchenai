package com.kitchenai.ui.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.NoParams
import com.kitchenai.shared.domain.usecase.kitchen.EnsureKitchenUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.session.EnsureSessionUseCase
import com.kitchenai.shared.domain.usecase.session.ObserveSessionUseCase
import com.kitchenai.ui.presentation.common.PendingDisplayName
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_own_data
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves the session and writes what a first launch needs before any screen reads `users/{uid}`,
 * and keeps the user in a kitchen afterwards: a leave or a removal makes the kitchen listener
 * report none, and the replacement is provisioned here, never by a screen (`docs/session.md`).
 */
class SessionViewModel(
    private val ensureSession: EnsureSessionUseCase,
    private val kitchen: SessionKitchenDelegate,
    private val observeSession: ObserveSessionUseCase,
    private val observeUserProfile: ObserveUserProfileUseCase,
    private val saveUserProfile: SaveUserProfileUseCase,
    private val pendingDisplayName: PendingDisplayName,
    private val time: TimeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    // The uid every listener here is scoped to; a Google sign-in or a sign-out changes it mid-session.
    private val activeUserId = MutableStateFlow<UserId?>(null)
    private val profile = MutableStateFlow<UserProfile?>(null)
    private var languageTags: List<String> = emptyList()
    private var defaultListName: String = ""
    private var bootstrap: Job? = null
    private var watchingSession = false

    // Lets `establish` tell a retry of the same uid (listeners kept) from a different one (listeners restarted).
    private var listenedUserId: UserId? = null
    private var userListeners: Job? = null

    // Set per uid by `establish`: a profile write failing at cold start is `Failed`, after a switch `SwitchFailed`.
    private var switched = false

    // Bootstrap and the profile error listener both reach createProfile(), so every check-then-act
    // over these flags holds this lock, never across a Firestore write. `establish` resets them per uid.
    private val flags = Mutex()
    private var profileMissing = false
    private var creatingProfile = false

    // Bootstrap, the session watcher and a switch retry can all reach `establish`; this serialises them.
    private val setup = Mutex()

    /** Idempotent: a configuration change composes the gate again, and a second sign-in is a second account. */
    fun start(
        languageTags: List<String>,
        defaultListName: String,
    ) {
        if (bootstrap != null) return
        this.languageTags = languageTags
        this.defaultListName = defaultListName
        watchPendingDisplayName()
        bootstrap = launchBootstrap()
    }

    /** Only from a failure: `Failed` re-runs the bootstrap, `SwitchFailed` re-follows the session without a sign-in. */
    fun retry() {
        when (_state.value) {
            is SessionUiState.Failed -> bootstrap = launchBootstrap()
            is SessionUiState.SwitchFailed -> {
                _state.value = SessionUiState.Loading
                viewModelScope.launch { follow(observeSession().first()) }
            }
            else -> return
        }
    }

    private fun launchBootstrap(): Job =
        viewModelScope.launch {
            _state.value = SessionUiState.Loading
            val userId =
                when (val session = ensureSession(NoParams)) {
                    is AppResult.Failure -> return@launch fail(session.error)
                    is AppResult.Success -> session.data.userId
                }
            establish(userId, coldStart = true)?.let { error -> return@launch fail(error) }
            watchSessionChanges()
        }

    /** Kitchen, default list and listeners for a uid; the caller owns [state] on failure. */
    private suspend fun establish(
        userId: UserId,
        coldStart: Boolean,
    ): AppError? =
        setup.withLock {
            // The profile that would carry a name does not exist yet, and the kitchen can be renamed later.
            provisionKitchen(userId, displayName = null)?.let { return@withLock it }

            // The same uid again keeps what its listener learned; a different one starts clean.
            if (activeUserId.value != userId) {
                flags.withLock {
                    profileMissing = false
                    creatingProfile = false
                }
                profile.value = null
                switched = !coldStart
                pendingDisplayName.dropUnless(userId)
            }
            activeUserId.value = userId
            _state.value = SessionUiState.Ready(userId)
            watchUser(userId)
            // A retry after a failed write: the listener will not repeat the NotFound.
            if (flags.withLock { profileMissing }) createProfile(userId)
            null
        }

    /** A kitchen for [userId] and its default list; idempotent, so any number of callers converge on one. */
    private suspend fun provisionKitchen(
        userId: UserId,
        displayName: String?,
    ): AppError? {
        val kitchenId =
            when (val ensured = kitchen.ensure(userId, displayName)) {
                is AppResult.Failure -> return ensured.error
                is AppResult.Success -> ensured.data.id
            }
        // Stored under the device's own tag: the app ships no translations, so another tag resolves to nothing.
        val labels = languageTags.take(1).associateWith { defaultListName }
        return (kitchen.ensureDefaultList(kitchenId, labels) as? AppResult.Failure)?.error
    }

    /** The uid can change after `Ready`: a Google sign-in swaps it (#186) and a sign-out clears it. */
    private fun watchSessionChanges() {
        if (watchingSession) return
        watchingSession = true
        viewModelScope.launch { observeSession().collect { session -> follow(session) } }
    }

    /**
     * Moves [state] to [session]'s uid once its setup completed; a failure is `SwitchFailed`, never `Ready`.
     * A sign-out re-runs [ensureSession], since every read would otherwise fail until a restart.
     */
    private suspend fun follow(session: Session) {
        val current = activeUserId.value
        if (session is Session.SignedIn && session.userId == current && _state.value is SessionUiState.Ready) return
        // A Google sign-in names its uid here even when the screen that offered the name is already gone.
        if (session is Session.SignedIn && !session.isAnonymous) pendingDisplayName.bind(session.userId)
        // Whatever follows, the previous uid's listener must not keep writing on its behalf.
        if (session !is Session.SignedIn || session.userId != current) detachActiveUser()
        val userId =
            when (session) {
                is Session.SignedIn -> session.userId
                Session.SignedOut ->
                    when (val reestablished = ensureSession(NoParams)) {
                        is AppResult.Failure -> return switchFailed(reestablished.error)
                        is AppResult.Success -> reestablished.data.userId
                    }
            }
        establish(userId, coldStart = false)?.let(::switchFailed)
    }

    private fun detachActiveUser() {
        userListeners?.cancel()
        userListeners = null
        listenedUserId = null
        activeUserId.value = null
    }

    /**
     * The data stream says the profile exists; the error stream is where a new user announces itself.
     * The kitchen listener works the same way: its NotFound is how a leave or a removal is learned.
     * The same uid is a no-op so a retry keeps its listeners; a different one replaces them.
     */
    private fun watchUser(userId: UserId) {
        if (listenedUserId == userId) return
        userListeners?.cancel()
        listenedUserId = userId
        userListeners =
            viewModelScope.launch {
                launch { observeUserProfile(userId).collect { loaded -> profile.value = loaded } }
                launch { observeUserProfile.errors(userId).collect { error -> onProfileError(userId, error) } }
                // The error stream first, so it is subscribed before the data stream can report anything.
                launch { kitchen.observe.errors(userId).collect { error -> onKitchenError(userId, error) } }
                // Never read: collecting is what keeps the listener open to report its NotFound.
                kitchen.observe(userId).launchIn(this)
            }
    }

    /**
     * A leave or a removal left the user with no kitchen: provision one without a restart. The listener
     * only says "none"; [EnsureKitchenUseCase] re-reads, so a stale or repeated event creates nothing.
     */
    private suspend fun onKitchenError(
        userId: UserId,
        error: AppError,
    ) {
        if (error !is AppError.NotFound) return
        setup.withLock {
            // Under the lock: the uid may have moved on, or a failed or restarting setup owns the state.
            if (userId != activeUserId.value || _state.value !is SessionUiState.Ready) return@withLock
            val name = profile.value?.takeIf { it.userId == userId }?.displayName
            // A failure of a uid that moved on during the write belongs to nobody.
            provisionKitchen(userId, name)?.let { failure -> if (userId == activeUserId.value) switchFailed(failure) }
        }
    }

    private suspend fun onProfileError(
        userId: UserId,
        error: AppError,
    ) {
        // An event already in flight when the uid moved on must not act for an identity no longer active.
        if (userId != activeUserId.value) return
        if (error !is AppError.NotFound) return
        flags.withLock { profileMissing = true }
        createProfile(userId)
    }

    /** Written once: claiming and performing are separate steps so the lock never spans the round trip. */
    private suspend fun createProfile(userId: UserId) {
        if (userId != activeUserId.value) return
        val claimed =
            flags.withLock {
                if (creatingProfile || profile.value != null) {
                    false
                } else {
                    creatingProfile = true
                    true
                }
            }
        if (!claimed) return
        // Created with the name so one sign-in never writes the same document twice.
        val pending = pendingDisplayName.current.value?.takeIf { it.userId == userId }
        val created = UserProfile.newFor(userId, languageTags, time.now()).copy(displayName = pending?.name)
        when (val result = saveUserProfile(created)) {
            is AppResult.Success -> pending?.let(pendingDisplayName::consume)
            is AppResult.Failure -> failProfileWrite(userId, result.error)
        }
    }

    // The uid may have moved on during the write: then neither the failure nor the flag is the old uid's to touch.
    private suspend fun failProfileWrite(
        userId: UserId,
        error: AppError,
    ) {
        val stillActive =
            flags.withLock {
                (userId == activeUserId.value).also { active -> if (active) creatingProfile = false }
            }
        if (!stillActive) return
        if (switched) switchFailed(error) else fail(error)
    }

    private fun watchPendingDisplayName() {
        viewModelScope.launch {
            combine(pendingDisplayName.current, profile, ::Pair).collect { (pending, loaded) ->
                if (pending != null && loaded != null) applyPendingDisplayName(pending, loaded)
            }
        }
    }

    // Never a failure state: a name that did not save stays pending until the profile next emits.
    private suspend fun applyPendingDisplayName(
        pending: PendingDisplayName.Entry,
        loaded: UserProfile,
    ) {
        if (pending.userId != loaded.userId || loaded.userId != activeUserId.value) return
        if (loaded.displayName == pending.name) return pendingDisplayName.consume(pending)
        if (saveUserProfile(loaded.copy(displayName = pending.name)) is AppResult.Success) {
            pendingDisplayName.consume(pending)
        }
    }

    private fun fail(error: AppError) {
        _state.value = SessionUiState.Failed(error.describe(Res.string.error_unauthorized_own_data))
    }

    private fun switchFailed(error: AppError) {
        _state.value = SessionUiState.SwitchFailed(error.describe(Res.string.error_unauthorized_own_data))
    }
}
