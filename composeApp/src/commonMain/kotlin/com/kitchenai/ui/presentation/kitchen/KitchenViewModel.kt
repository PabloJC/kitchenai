package com.kitchenai.ui.presentation.kitchen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.kitchen_member_unnamed
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Who is in the caller's kitchen, and the four actions membership offers: join another one,
 * leave this one, and — for its owner — remove someone or hand out a new code.
 *
 * No write copies its result into [kitchen]: the listener is the only source that field ever
 * takes, the same choice `ShoppingViewModel` makes for the same reason — a local copy would look
 * like an optimistic update and behave like a divergence the next remote echo has to correct.
 */
class KitchenViewModel(
    private val observeKitchen: ObserveKitchenUseCase,
    private val observeProfile: ObserveUserProfileUseCase,
    private val writes: KitchenWritesDelegate,
) : ViewModel() {
    // Null while the listener has not answered yet, and again once it reports the viewer has no
    // kitchen (a genuine NotFound): the moment between a leave and the session provisioning a new one.
    private val kitchen = MutableStateFlow<Kitchen?>(null)
    private val answered = MutableStateFlow(false)
    private val listenerFailure = MutableStateFlow<UiText?>(null)

    private val joinCodeInput = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val writeFailure = MutableStateFlow<UiText?>(null)
    private val confirming = MutableStateFlow<KitchenConfirmation?>(null)

    private val eventChannel = Channel<KitchenEvent>(Channel.BUFFERED)
    val events: Flow<KitchenEvent> = eventChannel.receiveAsFlow()

    // Only read here: the session stays the single writer of the profile, this just names the joiner.
    private var profileName: String? = null

    private var started = false
    private var userId: UserId? = null
    private var kitchenListener: Job? = null

    private val kitchenState = combine(kitchen, answered, listenerFailure, ::KitchenListenerState)
    private val writeState = combine(busy, writeFailure, confirming, ::WriteState)

    val state: StateFlow<KitchenUiState> =
        combine(kitchenState, joinCodeInput, writeState, ::project)
            .stateIn(viewModelScope, SharingStarted.Eagerly, KitchenUiState())

    /** Idempotent: a configuration change composes the screen again, not a second listener. */
    fun start(userId: UserId) {
        if (started) return
        started = true
        this.userId = userId
        watchKitchen(userId)
        viewModelScope.launch {
            observeProfile(userId).collect { profile -> profileName = profile.displayName?.takeIf { it.isNotBlank() } }
        }
    }

    /** Re-subscribes the kitchen listener after it failed; the last good kitchen stays on screen meanwhile. */
    fun retry() {
        val uid = userId ?: return
        listenerFailure.value = null
        // Back to loading rather than "no kitchen yet": nothing has answered the new listener.
        if (kitchen.value == null) answered.value = false
        kitchenListener?.cancel()
        watchKitchen(uid)
    }

    private fun watchKitchen(userId: UserId) {
        kitchenListener =
            viewModelScope.launch {
                launch {
                    observeKitchen(userId).collect { loaded ->
                        kitchen.value = loaded
                        answered.value = true
                        listenerFailure.value = null
                    }
                }
                launch { observeKitchen.errors(userId).collect { error -> onKitchenError(error) } }
            }
    }

    fun onJoinCodeInputChange(text: String) {
        joinCodeInput.value = text
    }

    /** With no kitchen to leave behind there is nothing to warn about, so the join runs at once. */
    fun requestJoin() {
        if (joinCodeInput.value.isBlank()) return
        if (kitchen.value == null) join() else confirming.value = KitchenConfirmation.Join
    }

    fun requestLeave() {
        confirming.value = KitchenConfirmation.Leave
    }

    fun requestRemoveMember(memberId: UserId) {
        val name = state.value.kitchen?.members?.firstOrNull { it.id == memberId }?.name ?: return
        confirming.value = KitchenConfirmation.RemoveMember(memberId, name)
    }

    fun requestRegenerateJoinCode() {
        confirming.value = KitchenConfirmation.RegenerateCode
    }

    /** Runs what was asked about; the request methods above are the only way anything gets here. */
    fun confirm() {
        val asked = confirming.value ?: return
        confirming.value = null
        when (asked) {
            KitchenConfirmation.Leave -> leave()
            is KitchenConfirmation.RemoveMember -> removeMember(asked.memberId, asked.name)
            KitchenConfirmation.Join -> join()
            KitchenConfirmation.RegenerateCode -> regenerateJoinCode()
        }
    }

    fun dismissConfirmation() {
        confirming.value = null
    }

    fun join() {
        val uid = userId ?: return
        val raw = joinCodeInput.value
        if (raw.isBlank()) return
        when (val code = KitchenJoinCode.normalised(raw)) {
            is AppResult.Failure -> writeFailure.value = code.error.describeKitchenError()
            is AppResult.Success ->
                write(describeError = AppError::describeJoinError, onSuccess = ::onJoined) {
                    writes.join(uid, displayName = profileName, code.data)
                }
        }
    }

    fun leave() {
        val uid = userId ?: return
        write(onSuccess = { announce(KitchenEvent.Left) }) { writes.leave(uid) }
    }

    fun removeMember(
        memberId: UserId,
        name: UiText = UiText.of(Res.string.kitchen_member_unnamed),
    ) {
        val uid = userId ?: return
        write(onSuccess = { announce(KitchenEvent.MemberRemoved(name)) }) { writes.removeMember(uid, memberId) }
    }

    fun regenerateJoinCode() {
        val uid = userId ?: return
        write(onSuccess = { announce(KitchenEvent.CodeRegenerated) }) { writes.regenerateJoinCode(uid) }
    }

    private fun onJoined() {
        joinCodeInput.value = ""
        announce(KitchenEvent.Joined)
    }

    private fun announce(event: KitchenEvent) {
        eventChannel.trySend(event)
    }

    /**
     * The query behind [ObserveKitchenUseCase] answers empty rather than throwing when the
     * viewer belongs to no kitchen, which the contract states as [AppError.NotFound] — the state
     * this screen renders as "join one below", not as a broken listener.
     */
    private fun onKitchenError(error: AppError) {
        answered.value = true
        if (error is AppError.NotFound) {
            kitchen.value = null
            listenerFailure.value = null
        } else {
            // The last good kitchen stays on screen: a transient failure is reported above it,
            // not mistaken for the viewer having left.
            listenerFailure.value = error.describeKitchenError()
        }
    }

    /** Every write goes through here, so none can forget to report its failure or double-fire while busy. */
    private fun write(
        describeError: (AppError) -> UiText = AppError::describeKitchenError,
        onSuccess: () -> Unit = {},
        block: suspend () -> AppResult<*>,
    ) {
        if (busy.value) return
        busy.value = true
        writeFailure.value = null
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Failure -> writeFailure.value = describeError(result.error)
                is AppResult.Success -> onSuccess()
            }
            busy.value = false
        }
    }

    private fun project(
        kitchenState: KitchenListenerState,
        joinCodeInput: String,
        writeState: WriteState,
    ): KitchenUiState =
        KitchenUiState(
            isLoading = !kitchenState.answered,
            kitchen = kitchenState.kitchen?.let { loaded -> userId?.let { uid -> loaded.toUi(uid) } },
            joinCodeInput = joinCodeInput,
            isBusy = writeState.busy,
            // The write just attempted speaks first: a stale listener banner must not hide why
            // the action the user just took was refused.
            error = writeState.failure ?: kitchenState.listenerFailure,
            // Only the listener can be reopened; a refused write is answered by tapping its action again.
            canRetry = writeState.failure == null && kitchenState.listenerFailure != null,
            confirming = writeState.confirming,
        )
}

/** What the screen's own writes add on top of the listener: in flight, refused, or waiting for a yes. */
private data class WriteState(
    val busy: Boolean,
    val failure: UiText?,
    val confirming: KitchenConfirmation?,
)

/** The three parts of what the kitchen listener has produced so far, combined once rather than three times. */
private data class KitchenListenerState(
    val kitchen: Kitchen?,
    val answered: Boolean,
    val listenerFailure: UiText?,
)
