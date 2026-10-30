package com.kitchenai.ui.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.usecase.profile.ObserveUserProfileUseCase
import com.kitchenai.shared.domain.usecase.profile.SetDisplayNameUseCase
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_action
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared by Profile's "Your name" row and the kitchen's own member row: one name, edited the
 * same way from either. The dialog closes only once the write lands, so a refusal stays readable.
 */
class DisplayNameViewModel(
    private val observeProfile: ObserveUserProfileUseCase,
    private val setDisplayName: SetDisplayNameUseCase,
) : ViewModel() {
    private val internalState = MutableStateFlow(DisplayNameUiState())
    val state: StateFlow<DisplayNameUiState> = internalState.asStateFlow()

    private var userId: UserId? = null
    private var languageTags: List<String> = emptyList()

    /** Idempotent: a configuration change composes the row again, not a second listener. */
    fun start(
        userId: UserId,
        languageTags: List<String>,
    ) {
        this.languageTags = languageTags
        if (this.userId == userId) return
        this.userId = userId
        viewModelScope.launch {
            observeProfile(userId).collect { profile ->
                internalState.update { it.copy(name = profile.displayName?.takeIf { name -> name.isNotBlank() }) }
            }
        }
    }

    fun edit() = internalState.update { it.copy(isEditing = true, error = null) }

    fun cancel() = internalState.update { it.copy(isEditing = false, error = null) }

    fun save(name: String) {
        val uid = userId ?: return
        if (internalState.value.isSaving) return
        internalState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = setDisplayName(uid, name, languageTags)) {
                is AppResult.Failure ->
                    internalState.update {
                        it.copy(isSaving = false, error = result.error.describe(Res.string.error_unauthorized_action))
                    }
                // The name itself arrives through the profile listener, the one source it is read from.
                is AppResult.Success -> internalState.update { it.copy(isSaving = false, isEditing = false) }
            }
        }
    }
}
