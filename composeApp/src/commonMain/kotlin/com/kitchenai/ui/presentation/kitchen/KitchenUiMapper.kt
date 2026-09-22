package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_invalid_field
import com.kitchenai.ui.resources.error_unauthorized_action
import com.kitchenai.ui.resources.kitchen_invalid_code
import com.kitchenai.ui.resources.kitchen_leave_disabled_owner

/** Mirrors `LeaveKitchenUseCase`'s own rule, so the reason is on screen before a tap is ever refused. */
internal fun Kitchen.toUi(viewer: UserId): KitchenUi {
    val ownedByViewer = ownerId == viewer
    val strandsIfLeft = ownedByViewer && memberIds.size > 1
    return KitchenUi(
        joinCode = joinCode.value,
        members = memberIds.sortedWith(memberOrder()).map { id -> memberUi(id, viewer) },
        isOwner = ownedByViewer,
        canLeave = !strandsIfLeft,
        leaveDisabledReason = if (strandsIfLeft) UiText.of(Res.string.kitchen_leave_disabled_owner) else null,
    )
}

/** The owner first, then everyone else by the name they are shown under. */
private fun Kitchen.memberOrder(): Comparator<UserId> =
    compareByDescending<UserId> { it == ownerId }.thenBy { memberDisplayNames[it.value] ?: it.value }

private fun Kitchen.memberUi(
    id: UserId,
    viewer: UserId,
): KitchenMemberUi =
    KitchenMemberUi(
        id = id,
        name = memberDisplayNames[id.value] ?: id.value,
        isSelf = id == viewer,
        isOwner = id == ownerId,
        canRemove = ownerId == viewer && id != ownerId,
    )

/**
 * [AppError.Validation("kitchen", ...)][AppError.Validation] only ever means one thing today —
 * `LeaveKitchenUseCase`'s owner-with-other-members refusal, reached reactively here because
 * `JoinKitchenUseCase` leaves the caller's current kitchen before joining another. Reusing
 * [kitchen_leave_disabled_owner] keeps this in the same (translated) words as the proactive,
 * listener-driven version of the same rule instead of the generic "Invalid kitchen: <reason>"
 * template interpolating an untranslated English literal.
 */
internal fun AppError.describeKitchenError(): UiText =
    describe(Res.string.error_unauthorized_action) { validation ->
        if (validation.field == "kitchen") {
            UiText.of(Res.string.kitchen_leave_disabled_owner)
        } else {
            UiText.of(Res.string.error_invalid_field, validation.field, validation.reason)
        }
    }

/** A bad or already-consumed code fails as [AppError.NotFound]; every other error keeps its own wording. */
internal fun AppError.describeJoinError(): UiText =
    if (this is AppError.NotFound) UiText.of(Res.string.kitchen_invalid_code) else describeKitchenError()
