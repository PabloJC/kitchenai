package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.domain.model.Appearance

/** Following the system until the stored choice is read, which is what a first launch looks like anyway. */
data class AppearanceUiState(
    val appearance: Appearance = Appearance.SYSTEM,
    val error: UiText? = null,
) {
    fun isDark(systemIsDark: Boolean): Boolean =
        when (appearance) {
            Appearance.SYSTEM -> systemIsDark
            Appearance.LIGHT -> false
            Appearance.DARK -> true
        }
}
