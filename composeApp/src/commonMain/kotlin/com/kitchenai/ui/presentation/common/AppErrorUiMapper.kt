package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_conflict
import com.kitchenai.ui.resources.error_invalid_input
import com.kitchenai.ui.resources.error_no_connection
import com.kitchenai.ui.resources.error_not_found
import com.kitchenai.ui.resources.error_timeout
import com.kitchenai.ui.resources.error_unknown
import org.jetbrains.compose.resources.StringResource

/**
 * The `AppError` to [UiText] mapping every screen needs. [unauthorized] is the one branch that
 * genuinely differs per screen — what "you may not do this" says depends on what "this" is, and
 * flattening it would make every screen say the same wrong thing for a different reason.
 *
 * [validation] lets a screen word its own refusal instead of the generic sentence, the way a cook
 * refused for missing ingredients already does; returning null falls back to it.
 *
 * Cause, `resource`, `field` and `reason` are dropped in every branch on purpose: they are
 * developer-facing English and identifiers, and this ends up on screen.
 */
internal fun AppError.describe(
    unauthorized: StringResource,
    validation: ((AppError.Validation) -> UiText?)? = null,
): UiText =
    when (this) {
        is AppError.Network -> UiText.of(Res.string.error_no_connection)
        is AppError.Timeout -> UiText.of(Res.string.error_timeout)
        is AppError.Unauthorized -> UiText.of(unauthorized)
        is AppError.NotFound -> UiText.of(Res.string.error_not_found)
        is AppError.Validation -> validation?.invoke(this) ?: UiText.of(Res.string.error_invalid_input)
        is AppError.Conflict -> UiText.of(Res.string.error_conflict)
        is AppError.Unknown -> UiText.of(Res.string.error_unknown)
    }
