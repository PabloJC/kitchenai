package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.UiText

/**
 * Everything the kitchen screen draws. [kitchen] is null both while the listener has not
 * answered yet and once the viewer has left with nothing new provisioned — [isLoading] is what
 * tells the two apart, and the join field below stays usable through either.
 */
data class KitchenUiState(
    val isLoading: Boolean = true,
    val kitchen: KitchenUi? = null,
    val joinCodeInput: String = "",
    val isBusy: Boolean = false,
    val error: UiText? = null,
)

/**
 * The kitchen resolved for its viewer: who they are decides which of the four actions this
 * screen offers apply to them, so the mapper settles that once rather than every composable
 * asking again.
 */
data class KitchenUi(
    val joinCode: String,
    val members: List<KitchenMemberUi>,
    val isOwner: Boolean,
    val canLeave: Boolean,
    val leaveDisabledReason: UiText?,
)

/** [canRemove] is the owner's own action on someone else; it is never true for the viewer's own row. */
data class KitchenMemberUi(
    val id: UserId,
    val name: String,
    val isSelf: Boolean,
    val isOwner: Boolean,
    val canRemove: Boolean,
)
