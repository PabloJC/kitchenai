package com.kitchenai.ui.presentation.suggestions.cooking

import com.kitchenai.shared.domain.model.RecipeId
import com.kitchenai.ui.platform.COOKING_ALARM_ID_PREFIX
import kotlin.time.Instant

/** A step's timer. [endsAt] is wall-clock: a monotonic mark stops while iOS sleeps and comes back late. */
data class RunningTimer(
    val recipeId: RecipeId,
    val step: Int,
    val endsAt: Instant,
) {
    /** One per recipe and step, which is what restarting a step's timer replaces. */
    val alarmId: String get() = "$COOKING_ALARM_ID_PREFIX${recipeId.value}-$step"
}
