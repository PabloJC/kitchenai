package com.kitchenai.ui.presentation.onboarding

import com.kitchenai.ui.presentation.common.UiText

data class OnboardingUiState(
    val status: OnboardingStatus = OnboardingStatus.CHECKING,
    val step: OnboardingStep = OnboardingStep.VALUE,
    /** The whole catalogue, by name: small enough today to offer every entry as a chip. */
    val staples: List<StapleUi> = emptyList(),
    /** The chosen staples are being written; Next is ignored meanwhile. */
    val isAdding: Boolean = false,
    val error: UiText? = null,
) {
    val chosenCount: Int get() = staples.count { it.isChosen }
}
