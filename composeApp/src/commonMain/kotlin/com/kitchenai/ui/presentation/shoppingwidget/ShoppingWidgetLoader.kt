package com.kitchenai.ui.presentation.shoppingwidget

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.core.flatMap
import com.kitchenai.shared.domain.model.Ingredient
import com.kitchenai.shared.domain.model.ShoppingItemId
import com.kitchenai.shared.domain.model.ShoppingSnapshot
import com.kitchenai.shared.domain.usecase.pantry.ObserveIngredientsUseCase
import com.kitchenai.shared.domain.usecase.shopping.GetShoppingSnapshotUseCase
import com.kitchenai.shared.domain.usecase.shopping.SetShoppingItemCheckedUseCase
import com.kitchenai.ui.presentation.common.LabelResolver
import com.kitchenai.ui.presentation.shopping.label
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The widget's ViewModel, minus the lifecycle: a widget is drawn from one read, not a stream.
 * Each call reads the list afresh, so a tick never lands on a list the user has since left.
 */
class ShoppingWidgetLoader(
    private val snapshot: GetShoppingSnapshotUseCase,
    private val ingredients: ObserveIngredientsUseCase,
    private val setChecked: SetShoppingItemCheckedUseCase,
) {
    suspend fun load(languageTags: List<String>): ShoppingWidgetState =
        when (val read = snapshot()) {
            is AppResult.Failure ->
                if (read.error is AppError.Unauthorized) ShoppingWidgetState.NothingYet else ShoppingWidgetState.Failed
            is AppResult.Success -> read.data?.let { linesOf(it, languageTags) } ?: ShoppingWidgetState.NothingYet
        }

    /** Ticks, never toggles: a widget drawn before a tick elsewhere writes the same value again. */
    suspend fun check(rawItemId: String): AppResult<Unit> =
        ShoppingItemId.of(rawItemId).flatMap { itemId ->
            snapshot().flatMap { read ->
                if (read == null) {
                    AppResult.Failure(AppError.NotFound("shoppingList"))
                } else {
                    setChecked(read.kitchenId, read.listId, itemId, checked = true)
                }
            }
        }

    private suspend fun linesOf(
        snapshot: ShoppingSnapshot,
        languageTags: List<String>,
    ): ShoppingWidgetState.Lines {
        val labels = LabelResolver(ingredients = catalogue(), languageTags = languageTags)
        val lines = snapshot.pending.map { item -> ShoppingWidgetLine(item.id, item.label(labels)) }
        return ShoppingWidgetState.Lines(lines)
    }

    // A failed listener never emits. Waiting is bounded so the names fall back to their ids instead.
    private suspend fun catalogue(): List<Ingredient> =
        withTimeoutOrNull(CATALOGUE_WAIT) { ingredients().first() }.orEmpty()

    private companion object {
        val CATALOGUE_WAIT = 5.seconds
    }
}
