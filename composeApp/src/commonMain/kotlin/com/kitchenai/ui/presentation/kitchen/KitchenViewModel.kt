package com.kitchenai.ui.presentation.kitchen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.kitchen.ObserveKitchenUseCase
import com.kitchenai.ui.presentation.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val writes: KitchenWritesDelegate,
) : ViewModel() {
    // Null while the listener has not answered yet; also set back to null once it reports the
    // viewer has no kitchen at all (a genuine NotFound, not a transient failure) — the state a
    // successful leave with nothing new provisioned leaves behind.
    private val kitchen = MutableStateFlow<Kitchen?>(null)
    private val answered = MutableStateFlow(false)
    private val listenerFailure = MutableStateFlow<UiText?>(null)

    private val joinCodeInput = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val writeFailure = MutableStateFlow<UiText?>(null)

    private var started = false
    private var userId: UserId? = null

    private val kitchenState = combine(kitchen, answered, listenerFailure, ::KitchenListenerState)

    val state: StateFlow<KitchenUiState> =
        combine(kitchenState, joinCodeInput, busy, writeFailure, ::project)
            .stateIn(viewModelScope, SharingStarted.Eagerly, KitchenUiState())

    /** Idempotent: a configuration change composes the screen again, not a second listener. */
    fun start(userId: UserId) {
        if (started) return
        started = true
        this.userId = userId
        viewModelScope.launch {
            observeKitchen(userId).collect { loaded ->
                kitchen.value = loaded
                answered.value = true
                listenerFailure.value = null
            }
        }
        viewModelScope.launch {
            observeKitchen.errors(userId).collect { error -> onKitchenError(error) }
        }
    }

    fun onJoinCodeInputChange(text: String) {
        joinCodeInput.value = text
    }

    fun join() {
        val uid = userId ?: return
        val raw = joinCodeInput.value.trim()
        if (raw.isEmpty()) return
        when (val code = KitchenJoinCode.of(raw)) {
            is AppResult.Failure -> writeFailure.value = code.error.describeKitchenError()
            is AppResult.Success ->
                write(describeError = AppError::describeJoinError, onSuccess = { joinCodeInput.value = "" }) {
                    writes.join(uid, displayName = null, code.data)
                }
        }
    }

    fun leave() {
        val uid = userId ?: return
        write { writes.leave(uid) }
    }

    fun removeMember(memberId: UserId) {
        val uid = userId ?: return
        write { writes.removeMember(uid, memberId) }
    }

    fun regenerateJoinCode() {
        val uid = userId ?: return
        write { writes.regenerateJoinCode(uid) }
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
        busy: Boolean,
        writeFailure: UiText?,
    ): KitchenUiState =
        KitchenUiState(
            isLoading = !kitchenState.answered,
            kitchen = kitchenState.kitchen?.let { loaded -> userId?.let { uid -> loaded.toUi(uid) } },
            joinCodeInput = joinCodeInput,
            isBusy = busy,
            // The write just attempted speaks first: a stale listener banner must not hide why
            // the action the user just took was refused.
            error = writeFailure ?: kitchenState.listenerFailure,
        )
}

/** The three parts of what the kitchen listener has produced so far, combined once rather than three times. */
private data class KitchenListenerState(
    val kitchen: Kitchen?,
    val answered: Boolean,
    val listenerFailure: UiText?,
)
