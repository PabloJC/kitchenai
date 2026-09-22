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
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingListUseCase
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_own_data
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves the session and writes the documents a first launch needs, before any screen reads
 * `users/{uid}`.
 */
class SessionViewModel(
    private val ensureSession: EnsureSessionUseCase,
    private val ensureKitchen: EnsureKitchenUseCase,
    private val ensureDefaultShoppingList: EnsureDefaultShoppingListUseCase,
    private val observeSession: ObserveSessionUseCase,
    private val observeUserProfile: ObserveUserProfileUseCase,
    private val saveUserProfile: SaveUserProfileUseCase,
    private val time: TimeProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    // The uid every listener in this class is scoped to — not just what [start] first resolves.
    // Google sign-in swaps the Firebase user outright rather than linking (#186), and signing
    // out clears it, so the uid this app runs as can change while [state] is already `Ready`.
    // [watchSessionChanges] is what keeps this (and therefore [state]) current instead of every
    // screen below `SessionGate` reading `users/{uid}` and kitchen data under a uid that Firebase
    // itself has already moved on from.
    private val activeUserId = MutableStateFlow<UserId?>(null)
    private val profile = MutableStateFlow<UserProfile?>(null)
    private var languageTags: List<String> = emptyList()
    private var defaultListName: String = ""
    private var bootstrap: Job? = null
    private var watchingSession = false

    // Which uid the profile listener job is currently subscribed for, so `establish` can tell
    // "the same uid again" (a retry — the listener must not restart, see the test of that name)
    // from "a genuinely different uid" (a reactive swap — the previous one's listener must not
    // keep running under the new uid's flags).
    private var profileListenerUserId: UserId? = null
    private var profileListeners: Job? = null

    // The two flags below are read and written from genuinely independent coroutines —
    // bootstrap and the profile error listener both reach createProfile() on their own — so
    // every check-then-act over them happens under this lock, not because of which dispatcher
    // any of them runs on. It is never held across a write to Firestore. They belong to
    // whichever uid is currently active: `establish` resets them when the uid actually changes.
    private val flags = Mutex()
    private var profileMissing = false
    private var creatingProfile = false

    /**
     * Idempotent: a configuration change composes the gate again, and a second anonymous
     * sign-in would be a second account.
     */
    fun start(
        languageTags: List<String>,
        defaultListName: String,
    ) {
        if (bootstrap != null) return
        this.languageTags = languageTags
        this.defaultListName = defaultListName
        bootstrap = launchBootstrap()
    }

    /**
     * Only from a failure, and it re-runs the whole bootstrap — including the sign-in and the
     * default list, which a failure after [SessionUiState.Ready] has already done. Both are
     * idempotent, which is what makes repeating them safe rather than merely cheap.
     */
    fun retry() {
        if (_state.value !is SessionUiState.Failed) return
        bootstrap = launchBootstrap()
    }

    private fun launchBootstrap(): Job =
        viewModelScope.launch {
            _state.value = SessionUiState.Loading
            val userId =
                when (val session = ensureSession(NoParams)) {
                    is AppResult.Failure -> return@launch fail(session.error)
                    is AppResult.Success -> session.data.userId
                }
            if (!establish(userId)) return@launch
            watchSessionChanges()
        }

    /**
     * Everything a uid needs before a screen may read `users/{uid}`: a kitchen, a default
     * shopping list, and its own profile listener. Runs for the uid [start] first resolves, for
     * a [retry] of that same uid, and again for whichever uid [watchSessionChanges] sees next.
     */
    private suspend fun establish(userId: UserId): Boolean {
        // No display name yet at this point — the profile that would carry one is created
        // further down, and a kitchen without one can still be shown one later.
        val kitchenId =
            when (val kitchen = ensureKitchen(userId, displayName = null)) {
                is AppResult.Failure -> {
                    fail(kitchen.error)
                    return false
                }
                is AppResult.Success -> kitchen.data.id
            }
        // The name is stored under the device's own tag: the app ships no translations of its
        // own, and a name under a tag nobody reads resolves to nothing.
        val labels = languageTags.take(1).associateWith { defaultListName }
        val list = ensureDefaultShoppingList(kitchenId, labels)
        if (list is AppResult.Failure) {
            fail(list.error)
            return false
        }

        // A retry of the uid already active must not reset what its still-subscribed listener
        // already learned (see "a retry after Ready" below); a genuinely different uid must not
        // inherit the previous one's missing-profile or already-creating state.
        if (activeUserId.value != userId) {
            flags.withLock {
                profileMissing = false
                creatingProfile = false
            }
            profile.value = null
        }
        activeUserId.value = userId
        _state.value = SessionUiState.Ready(userId)
        watchProfile(userId)
        // A retry after a failed write: the listener will not repeat the NotFound.
        if (flags.withLock { profileMissing }) createProfile(userId)
        return true
    }

    /**
     * The Firebase uid this app runs as can change after [SessionUiState.Ready]: a Google
     * sign-in swaps it outright rather than linking (#186), and signing out clears it. Every
     * screen below `SessionGate` reads `users/{uid}` and kitchen data keyed to whatever uid
     * [state] carries, so this is what keeps that uid current instead of frozen at whatever
     * [ensureSession] first resolved. A sign-out re-runs [ensureSession] the same way a cold
     * start would, rather than leaving every read failing with permission-denied until the app
     * is restarted.
     */
    private fun watchSessionChanges() {
        if (watchingSession) return
        watchingSession = true
        viewModelScope.launch {
            observeSession().collect { session ->
                val userId = session.resolvedOrReestablished() ?: return@collect
                if (userId != activeUserId.value) establish(userId)
            }
        }
    }

    private suspend fun Session.resolvedOrReestablished(): UserId? =
        when (this) {
            is Session.SignedIn -> userId
            Session.SignedOut ->
                when (val reestablished = ensureSession(NoParams)) {
                    is AppResult.Failure -> {
                        fail(reestablished.error)
                        null
                    }
                    is AppResult.Success -> reestablished.data.userId
                }
        }

    /**
     * Both streams, as the contract requires. The data one says the profile exists; the error
     * one is the only place a new user announces itself, because the repository does not write
     * on read. A call for the uid already watched is a no-op — the listener from a previous
     * attempt must outlive a retry — but a different uid cancels it and starts fresh, so a
     * reactive swap never leaves the old uid's listener running alongside the new one's.
     */
    private fun watchProfile(userId: UserId) {
        if (profileListenerUserId == userId) return
        profileListeners?.cancel()
        profileListenerUserId = userId
        profileListeners =
            viewModelScope.launch {
                launch { observeUserProfile(userId).collect { loaded -> profile.value = loaded } }
                launch { observeUserProfile.errors(userId).collect { error -> onProfileError(userId, error) } }
            }
    }

    private suspend fun onProfileError(
        userId: UserId,
        error: AppError,
    ) {
        // A listener cancellation is cooperative: a event already in flight when the uid moved
        // on must not act on behalf of an identity that is no longer active.
        if (userId != activeUserId.value) return
        if (error !is AppError.NotFound) return
        flags.withLock { profileMissing = true }
        createProfile(userId)
    }

    /**
     * Written once: two NotFound emissions are one missing document, not two, and a retry can
     * race the listener that is still alive from the first attempt. Claiming the write and
     * performing it are separate steps so the lock never spans the round trip.
     */
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
        val result = saveUserProfile(UserProfile.newFor(userId, languageTags, time.now()))
        if (result is AppResult.Failure) {
            flags.withLock { creatingProfile = false }
            fail(result.error)
        }
    }

    private fun fail(error: AppError) {
        _state.value = SessionUiState.Failed(error.describe(Res.string.error_unauthorized_own_data))
    }
}
