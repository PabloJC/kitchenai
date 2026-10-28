package com.kitchenai.ui.presentation.quickadd

import com.kitchenai.shared.core.AppError
import com.kitchenai.shared.core.AppResult
import com.kitchenai.shared.domain.usecase.shopping.AddShoppingItemUseCase
import com.kitchenai.shared.domain.usecase.shopping.GetShoppingSnapshotUseCase

/**
 * Adds a line from outside the app, such as Siri, to the signed-in user's list. The text goes in
 * as typed, as the add field does when no ingredient is picked.
 */
class QuickAddToList(
    private val snapshot: GetShoppingSnapshotUseCase,
    private val addItem: AddShoppingItemUseCase,
) {
    suspend fun add(text: String): QuickAddOutcome {
        val name = text.trim()
        if (name.isEmpty()) return QuickAddOutcome.NOTHING_TO_ADD
        val list =
            when (val read = snapshot()) {
                is AppResult.Failure ->
                    return if (read.error is AppError.Unauthorized) QuickAddOutcome.NO_LIST else QuickAddOutcome.FAILED
                is AppResult.Success -> read.data ?: return QuickAddOutcome.NO_LIST
            }
        return when (addItem(list.kitchenId, list.listId, freeText = name)) {
            is AppResult.Success -> QuickAddOutcome.ADDED
            is AppResult.Failure -> QuickAddOutcome.FAILED
        }
    }
}
