package com.kitchenai.ui.presentation.kitchen

import com.kitchenai.shared.domain.model.Kitchen
import com.kitchenai.shared.domain.model.UserId
import com.kitchenai.shared.domain.model.isStrandedIfLeftBy
import com.kitchenai.ui.presentation.common.UiText
import com.kitchenai.ui.resources.Res
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
    val name = nameOf(id)
    return KitchenMemberUi(
        id = id,
        name = labelOf(name, isSelf),
        initials = name?.let(::initialsOf),
        isSelf = isSelf,
        isOwner = id == ownerId,
        canRemove = ownerId == viewer && id != ownerId,
    )
}

/** The first letter of the first two words: "Ana María López" is "AM". */
internal fun initialsOf(name: String): String? =
    name
        .split(' ')
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { null }

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
