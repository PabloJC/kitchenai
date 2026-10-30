package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.UiText

/** A membership change waiting for the user's yes: each one moves data out of someone's sight. */
sealed interface KitchenConfirmation {
    data object Leave : KitchenConfirmation

    data class RemoveMember(
        val memberId: UserId,
        val name: UiText,
    ) : KitchenConfirmation

    /** The owner gives up removing people and the code; only the new owner can hand it back. */
    data class MakeOwner(
        val memberId: UserId,
        val name: UiText,
    ) : KitchenConfirmation

    /** Joining leaves the current kitchen in the same transaction, so it is asked like a leave. */
    data object Join : KitchenConfirmation

    /** The old code stops resolving at once; anyone still holding it is locked out. */
    data object RegenerateCode : KitchenConfirmation
}
