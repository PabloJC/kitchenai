package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.isStrandedIfLeftBy
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.presentation.common.describe
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_invalid_field
import com.kitchenai.ui.resources.error_unauthorized_action
import com.kitchenai.ui.resources.kitchen_invalid_code
import com.kitchenai.ui.resources.kitchen_leave_disabled_owner
import com.kitchenai.ui.resources.kitchen_member_unnamed
import com.kitchenai.ui.resources.kitchen_you_suffix
import com.kitchenai.ui.resources.kitchen_you_unnamed

/** The viewer's own rule, [isStrandedIfLeftBy], so the reason is on screen before a tap is ever refused. */
internal fun Kitchen.toUi(viewer: UserId): KitchenUi {
    val strandsIfLeft = isStrandedIfLeftBy(viewer)
    return KitchenUi(
        joinCode = joinCode.value,
        members = memberIds.sortedWith(memberOrder()).map { id -> memberUi(id, viewer) },
        isOwner = ownerId == viewer,
        canLeave = !strandsIfLeft,
        leaveDisabledReason = if (strandsIfLeft) UiText.of(Res.string.kitchen_leave_disabled_owner) else null,
    )
}

/** The owner first, then the named by name, then whoever has none. */
private fun Kitchen.memberOrder(): Comparator<UserId> =
    compareByDescending<UserId> { it == ownerId }
        .thenBy(nullsLast<String>()) { nameOf(it) }
        .thenBy { it.value }

/** A blank entry is as good as none: it would render as an empty row. */
private fun Kitchen.nameOf(id: UserId): String? = memberDisplayNames[id.value]?.takeIf { it.isNotBlank() }

private fun Kitchen.memberUi(
    id: UserId,
    viewer: UserId,
): KitchenMemberUi {
    val isSelf = id == viewer
    return KitchenMemberUi(
        id = id,
        name = labelOf(nameOf(id), isSelf),
        isSelf = isSelf,
        isOwner = id == ownerId,
        canRemove = ownerId == viewer && id != ownerId,
    )
}

/** Never the raw id: it means nothing to a person reading the list. */
private fun labelOf(
    name: String?,
    isSelf: Boolean,
): UiText =
    when {
        name != null && isSelf -> UiText.of(Res.string.kitchen_you_suffix, name)
        name != null -> UiText.Raw(name)
        isSelf -> UiText.of(Res.string.kitchen_you_unnamed)
        else -> UiText.of(Res.string.kitchen_member_unnamed)
    }

/**
 * [AppError.Validation("kitchen", ...)][AppError.Validation] only ever means one thing today —
 * `LeaveKitchenUseCase`'s owner-with-other-members refusal, reached reactively here because
 * `JoinKitchenUseCase` applies it to the caller's current kitchen before joining another. Reusing
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
