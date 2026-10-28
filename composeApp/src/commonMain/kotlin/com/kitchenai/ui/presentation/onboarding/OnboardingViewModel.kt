package com.kitchenai.ui.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.map
import com.kitchenai.shared.domain.model.AppleIdToken
import com.kitchenai.shared.domain.model.AppleRawNonce
import com.kitchenai.shared.domain.model.GoogleAccessToken
import com.kitchenai.shared.domain.model.GoogleIdToken
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.IngredientId
import com.kitchenai.shared.domain.model.KitchenId
import com.kitchenai.shared.domain.model.KitchenJoinCode
import com.kitchenai.shared.domain.model.Session
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.presentation.common.describeJoinError
import com.kitchenai.ui.presentation.common.extractJoinCode
import com.kitchenai.ui.presentation.common.nameOf
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_action
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The first run, shown once per device and skippable from any page. An install whose kitchen
 * already holds something belongs to someone who has used the app, so it is marked done unseen.
 */
class OnboardingViewModel(
    private val reads: OnboardingReadsDelegate,
    private val writes: OnboardingWritesDelegate,
    private val account: OnboardingAccountDelegate,
) : ViewModel() {
    private val internalState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = internalState.asStateFlow()

    private val exitChannel = Channel<OnboardingExit>(Channel.BUFFERED)
    val exits: Flow<OnboardingExit> = exitChannel.receiveAsFlow()

    private var started = false
    private var userId: UserId? = null
    private var languageTags: List<String> = emptyList()
    private var kitchenId: KitchenId? = null
    private var catalogue: List<Ingredient> = emptyList()
    private var chosen: Set<IngredientId> = emptySet()

    /** Idempotent: a configuration change composes the gate again, not a second decision. */
    fun start(
        userId: UserId,
        languageTags: List<String>,
    ) {
        if (started) return
        started = true
        this.userId = userId
        this.languageTags = languageTags
        viewModelScope.launch { reads.kitchen(userId).collect { kitchen -> kitchenId = kitchen.id } }
        viewModelScope.launch {
            reads.ingredients().collect { loaded ->
                catalogue = loaded
                renderStaples()
            }
        }
        viewModelScope.launch { decide(userId) }
    }

    fun toggle(id: IngredientId) {
        chosen = if (id in chosen) chosen - id else chosen + id
        renderStaples()
    }

    /** Leaving the staples page is what writes them, so Back and Next never add twice. */
    fun next() {
        val current = internalState.value
        if (current.isAdding) return
        when (current.step) {
            OnboardingStep.VALUE -> internalState.update { it.copy(step = OnboardingStep.STAPLES, error = null) }
            OnboardingStep.STAPLES -> addChosen()
            OnboardingStep.PREFERENCES -> finish(OnboardingExit.IDEAS)
            OnboardingStep.JOIN -> join()
        }
    }

    fun back() {
        val previous =
            when (internalState.value.step) {
                OnboardingStep.VALUE -> return
                OnboardingStep.STAPLES -> OnboardingStep.VALUE
                OnboardingStep.PREFERENCES -> OnboardingStep.STAPLES
                OnboardingStep.JOIN -> OnboardingStep.VALUE
            }
        internalState.update { it.copy(step = previous, error = null) }
    }

    /** Anything chosen but not yet added stays out: skipping means skipping. */
    fun skip() = finish(OnboardingExit.IDEAS)

    fun setPreferences() = finish(OnboardingExit.PREFERENCES)

    /** An invited partner skips the staples: they are joining a kitchen that already has its own. */
    fun showJoin() = internalState.update { it.copy(step = OnboardingStep.JOIN, error = null) }

    /** A whole invite message pasted into the field leaves just its code behind. */
    fun onJoinCodeChange(text: String) = internalState.update { it.copy(joinCode = extractJoinCode(text) ?: text) }

    fun pasteJoinCode(clipboard: String) = onJoinCodeChange(clipboard.trim())

    /**
     * No "you will leave your current kitchen" question: the first run's kitchen is the empty one
     * the session just made, so there is nothing to leave behind.
     */
    fun join() {
        val uid = userId ?: return
        val raw = internalState.value.joinCode
        if (raw.isBlank() || internalState.value.isBusy) return
        when (val code = KitchenJoinCode.normalised(raw)) {
            is AppResult.Failure -> internalState.update { it.copy(error = code.error.describeJoinError()) }
            is AppResult.Success ->
                busy({ error -> error.describeJoinError() }) {
                    writes.join(uid, null, code.data).map { }
                }
        }
    }

    /**
     * A returning user on a fresh install: the guest made a moment ago holds nothing yet, so an
     * account with a kitchen of its own is switched to without Profile's "stays behind" question.
     */
    fun signInWithGoogle(
        token: GoogleIdToken,
        displayName: String?,
        accessToken: GoogleAccessToken? = null,
    ) = authenticate(displayName) {
        account.signInWithGoogle(token, accessToken).orOnConflict { account.switchToGoogleAccount(token, accessToken) }
    }

    /** Apple spends its nonce on a refused link, so a conflict asks the screen for the sheet once more. */
    fun signInWithApple(
        token: AppleIdToken,
        rawNonce: AppleRawNonce,
        displayName: String?,
    ) = authenticate(displayName) {
        val linked = account.signInWithApple(token, rawNonce)
        if (linked is AppResult.Failure && linked.error is AppError.Conflict) {
            internalState.update { it.copy(appleSwitchPending = true) }
            AppResult.Success(null)
        } else {
            linked
        }
    }

    fun switchToAppleAccount(
        token: AppleIdToken,
        rawNonce: AppleRawNonce,
        displayName: String?,
    ) {
        internalState.update { it.copy(appleSwitchPending = false) }
        authenticate(displayName) { account.switchToAppleAccount(token, rawNonce) }
    }

    /** A cancelled or failed sheet never reaches a use case but still owes the user a reason. */
    fun onSignInSheetFailed(error: AppError) =
        internalState.update {
            it.copy(appleSwitchPending = false, error = error.describe(Res.string.error_unauthorized_action))
        }

    private suspend fun AppResult<Session.SignedIn>.orOnConflict(
        switch: suspend () -> AppResult<Session.SignedIn>,
    ): AppResult<Session.SignedIn?> = if (this is AppResult.Failure && error is AppError.Conflict) switch() else this

    /** A success with no session is the Apple sheet being asked again: nothing to finish yet. */
    private fun authenticate(
        displayName: String?,
        attempt: suspend () -> AppResult<Session.SignedIn?>,
    ) {
        if (internalState.value.isBusy) return
        // Handed over before the sign-in: the name lands on whichever uid the sign-in ends on.
        displayName?.takeUnless(String::isBlank)?.let(account.pendingDisplayName::expect)
        busy(
            { error -> error.describe(Res.string.error_unauthorized_action) },
            onFailure = account.pendingDisplayName::dropUnbound,
        ) {
            when (val result = attempt()) {
                is AppResult.Failure -> result
                is AppResult.Success -> {
                    val signedIn = result.data ?: return@busy AppResult.Success(null)
                    account.pendingDisplayName.bind(signedIn.userId)
                    AppResult.Success(Unit)
                }
            }
        }
    }

    /** Runs [block]; a Unit success ends the first run on the pantry, a null one leaves the page as it is. */
    private fun busy(
        describeError: (AppError) -> UiText,
        onFailure: () -> Unit = {},
        block: suspend () -> AppResult<Unit?>,
    ) {
        internalState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val result = block()
            internalState.update { it.copy(isBusy = false) }
            when (result) {
                is AppResult.Failure -> {
                    onFailure()
                    internalState.update { it.copy(error = describeError(result.error)) }
                }
                is AppResult.Success -> if (result.data != null) finish(OnboardingExit.PANTRY)
            }
        }
    }

    private suspend fun decide(userId: UserId) {
        if (reads.completed().first()) {
            internalState.update { it.copy(status = OnboardingStatus.DONE) }
            return
        }
        // Bounded, and null-safe: a listener that never answers, or ends without a word, must not
        // hold the app on a spinner. Not knowing reads as a new user.
        val alreadyUsed =
            withTimeoutOrNull(EXISTING_USER_CHECK) {
                val kitchen = reads.kitchen(userId).firstOrNull() ?: return@withTimeoutOrNull false
                reads.pantry(kitchen.id).firstOrNull()?.isNotEmpty() == true
            }
        if (alreadyUsed == true) {
            // A failed write only means the same check runs again next launch.
            writes.complete()
            internalState.update { it.copy(status = OnboardingStatus.DONE) }
        } else {
            internalState.update { it.copy(status = OnboardingStatus.SHOWING) }
        }
    }

    private fun addChosen() {
        val picked = chosen.toList()
        if (picked.isEmpty()) {
            internalState.update { it.copy(step = OnboardingStep.PREFERENCES, error = null) }
            return
        }
        val kitchen = kitchenId
        if (kitchen == null) {
            // The kitchen listener has not answered yet: say so rather than leave Next doing nothing.
            internalState.update {
                it.copy(
                    error = AppError.NotFound("kitchen").describe(Res.string.error_unauthorized_action),
                )
            }
            return
        }
        internalState.update { it.copy(isAdding = true, error = null) }
        viewModelScope.launch {
            when (val added = writes.addStaples(kitchen, picked)) {
                is AppResult.Failure ->
                    internalState.update {
                        it.copy(isAdding = false, error = added.error.describe(Res.string.error_unauthorized_action))
                    }
                is AppResult.Success ->
                    internalState.update { it.copy(isAdding = false, step = OnboardingStep.PREFERENCES) }
            }
        }
    }

    private fun finish(exit: OnboardingExit) {
        if (internalState.value.status == OnboardingStatus.DONE) return
        viewModelScope.launch {
            // The exit happens whatever the write says: a flag that failed to save shows the flow once more, no worse.
            writes.complete()
            internalState.update { it.copy(status = OnboardingStatus.DONE) }
            exitChannel.send(exit)
        }
    }

    private fun renderStaples() {
        val resolver = LabelResolver(ingredients = catalogue, languageTags = languageTags)
        val staples =
            catalogue
                .map { ingredient -> StapleUi(ingredient.id, resolver.nameOf(ingredient), ingredient.id in chosen) }
                .sortedBy { it.label.lowercase() }
        internalState.update { it.copy(staples = staples) }
    }

    private companion object {
        val EXISTING_USER_CHECK = 3.seconds
    }
}
