package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.ui.presentation.common.UiText

/** What happens once and must not be replayed on the next recomposition: a done membership change or a copy. */
sealed interface KitchenEvent {
    data object Left : KitchenEvent

    data class MemberRemoved(val name: UiText) : KitchenEvent

    data class OwnerChanged(val name: UiText) : KitchenEvent

    data object Joined : KitchenEvent

    data object CodeRegenerated : KitchenEvent

    data object CodeCopied : KitchenEvent
}
