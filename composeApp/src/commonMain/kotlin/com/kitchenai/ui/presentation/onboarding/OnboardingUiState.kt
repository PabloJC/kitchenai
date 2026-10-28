package com.kitchenai.ui.presentation.onboarding

import com.kitchenai.ui.presentation.common.UiText

data class OnboardingUiState(
    val status: OnboardingStatus = OnboardingStatus.CHECKING,
    val step: OnboardingStep = OnboardingStep.VALUE,
    /** The whole catalogue, by name: small enough today to offer every entry as a chip. */
    val staples: List<StapleUi> = emptyList(),
    /** The chosen staples are being written; Next is ignored meanwhile. */
    val isAdding: Boolean = false,
    val joinCode: String = "",
    /** A sign-in or a join is running; their buttons wait meanwhile. */
    val isBusy: Boolean = false,
    /** Apple refused the link and spent its nonce: the sheet must answer once more, for the switch. */
    val appleSwitchPending: Boolean = false,
    val error: UiText? = null,
) {
    val chosenCount: Int get() = staples.count { it.isChosen }
}
