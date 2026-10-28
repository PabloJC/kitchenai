package com.kitchenai.ui.presentation.suggestions.cooking

import com.kitchenai.shared.domain.model.RecipeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Every step timer in the app, held outside cooking mode's ViewModel so leaving the screen does not
 * end them. A Koin single; it only remembers, and the system alarm is what rings.
 */
class RunningTimers {
    private val timers = MutableStateFlow<List<RunningTimer>>(emptyList())
    val running: StateFlow<List<RunningTimer>> = timers.asStateFlow()

    /** Starting a step's timer again replaces that step's, and only that one. */
    fun put(timer: RunningTimer) = timers.update { held -> held.filterNot { it.alarmId == timer.alarmId } + timer }

    fun remove(
        recipeId: RecipeId,
        step: Int,
    ) = timers.update { held -> held.filterNot { it.recipeId == recipeId && it.step == step } }

    fun of(recipeId: RecipeId): List<RunningTimer> = timers.value.filter { it.recipeId == recipeId }
}
