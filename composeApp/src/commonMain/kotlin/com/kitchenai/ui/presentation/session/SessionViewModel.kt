package com.kitchenai.ui.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.DispatcherProvider
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.UserProfile
import com.kitchenai.shared.domain.port.TimeProvider
import com.kitchenai.shared.domain.usecase.NoParams
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfile
import com.kitchenai.shared.domain.usecase.profile.SaveUserProfile
import com.kitchenai.shared.domain.usecase.session.EnsureSession
import com.kitchenai.shared.domain.usecase.shopping.EnsureDefaultShoppingList
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
    private val ensureSession: EnsureSession,
    private val ensureDefaultShoppingList: EnsureDefaultShoppingList,
    private val observeUserProfile: ObserveUserProfile,
    private val saveUserProfile: SaveUserProfile,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val profile = MutableStateFlow<UserProfile?>(null)
    private var languageTags: List<String> = emptyList()
    private var defaultListName: String = ""
    private var bootstrap: Job? = null

    // The three flags below are read and written from several coroutines on a multi-threaded
    // dispatcher, so every check-then-act over them happens under this lock. It is never held
    // across a write to Firestore.
    private val flags = Mutex()
    private var watching = false
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

    /** Only from a failure: retrying a session that already resolved would redo its writes. */
    fun retry() {
        if (_state.value !is SessionUiState.Failed) return
        bootstrap = launchBootstrap()
    }

    private fun launchBootstrap(): Job =
        viewModelScope.launch(dispatchers.default) {
            _state.value = SessionUiState.Loading
            val userId =
                when (val session = ensureSession(NoParams)) {
                    is AppResult.Failure -> return@launch fail(session.error)
                    is AppResult.Success -> session.data.userId
                }
            // The name is stored under the device's own tag: the app ships no translations of
            // its own, and a name under a tag nobody reads resolves to nothing.
            val labels = languageTags.take(1).associateWith { defaultListName }
            val list = ensureDefaultShoppingList(userId, labels)
            if (list is AppResult.Failure) return@launch fail(list.error)

            _state.value = SessionUiState.Ready(userId)
            watchProfile(userId)
            // A retry after a failed write: the listener will not repeat the NotFound.
            if (flags.withLock { profileMissing }) createProfile(userId)
        }

    /**
     * Both streams, as the contract requires. The data one says the profile exists; the error
     * one is the only place a new user announces itself, because the repository does not write
     * on read.
     */
    private suspend fun watchProfile(userId: UserId) {
        val alreadyWatching = flags.withLock { watching.also { watching = true } }
        if (alreadyWatching) return
        viewModelScope.launch(dispatchers.default) {
            observeUserProfile(userId).collect { loaded -> profile.value = loaded }
        }
        viewModelScope.launch(dispatchers.default) {
            observeUserProfile.errors(userId).collect { error -> onProfileError(userId, error) }
        }
    }

    private suspend fun onProfileError(
        userId: UserId,
        error: AppError,
    ) {
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
        _state.value = SessionUiState.Failed(error.describe())
    }
}

/** The cause is dropped on purpose: it can carry paths and identifiers, and this ends up on screen. */
private fun AppError.describe(): String =
    when (this) {
        is AppError.Network -> "No connection"
        is AppError.Unauthorized -> "This account is not allowed to read its own data"
        is AppError.NotFound -> "Cannot find $resource"
        is AppError.Validation -> "Invalid $field: $reason"
        is AppError.Unknown -> "Something went wrong"
    }
