package com.kitchenai.ui.presentation.common

import com.kitchenai.shared.core.AppError
import com.kitchenai.ui.resources.Res
import com.kitchenai.ui.resources.error_unauthorized_action
import com.kitchenai.ui.resources.kitchen_invalid_code
import com.kitchenai.ui.resources.kitchen_leave_disabled_owner

/**
 * [AppError.Validation("kitchen", ...)][AppError.Validation] only ever means one thing today —
 * `LeaveKitchenUseCase`'s owner-with-other-members refusal, reached reactively here because
 * `JoinKitchenUseCase` applies it to the caller's current kitchen before joining another. Reusing
 * [kitchen_leave_disabled_owner] keeps this in the same (translated) words as the proactive,
 * listener-driven version of the same rule; any other validation failure takes the generic sentence.
 */
internal fun AppError.describeKitchenError(): UiText =
    describe(Res.string.error_unauthorized_action) { validation ->
        if (validation.field == "kitchen") UiText.of(Res.string.kitchen_leave_disabled_owner) else null
    }

/** A bad or already-consumed code fails as [AppError.NotFound]; every other error keeps its own wording. */
internal fun AppError.describeJoinError(): UiText =
    if (this is AppError.NotFound) UiText.of(Res.string.kitchen_invalid_code) else describeKitchenError()
