package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.UiText

/**
 * Everything the kitchen screen draws. [kitchen] is null both while the listener has not
 * answered yet and between a leave and the session provisioning a replacement — [isLoading] is
 * what tells the two apart, and the join field below stays usable through either.
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

/** [name] is the label to draw, placeholder included; [canRemove] is the owner's action on others only. */
data class KitchenMemberUi(
    val id: UserId,
    val name: UiText,
    val isSelf: Boolean,
    val isOwner: Boolean,
    val canRemove: Boolean,
)
