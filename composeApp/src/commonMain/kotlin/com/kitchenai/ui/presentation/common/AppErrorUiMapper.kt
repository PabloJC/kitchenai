package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_invalid_field
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
 * [validation] lets a screen word its own refusal instead of the generic "Invalid `<field>`:
 * `<reason>`", the way a cook refused for missing ingredients already does.
 *
 * The cause is dropped in every branch on purpose: it can carry paths and identifiers, and this
 * ends up on screen.
 */
internal fun AppError.describe(
    unauthorized: StringResource,
    validation: ((AppError.Validation) -> UiText)? = null,
): UiText =
    when (this) {
        is AppError.Network -> UiText.of(Res.string.error_no_connection)
        is AppError.Timeout -> UiText.of(Res.string.error_timeout)
        is AppError.Unauthorized -> UiText.of(unauthorized)
        is AppError.NotFound -> UiText.of(Res.string.error_not_found, resource)
        is AppError.Validation -> validation?.invoke(this) ?: UiText.of(Res.string.error_invalid_field, field, reason)
        is AppError.Unknown -> UiText.of(Res.string.error_unknown)
    }
